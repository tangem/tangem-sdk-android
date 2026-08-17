package com.tangem.operations

import com.tangem.Log
import com.tangem.common.CompletionResult.Failure
import com.tangem.common.CompletionResult.Success
import com.tangem.common.UserCode
import com.tangem.common.UserCodeType
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.*
import com.tangem.common.extensions.calculateSha256
import com.tangem.common.extensions.guard
import com.tangem.common.json.MoshiJsonConverter
import com.tangem.common.services.TrustedCardsRepo
import com.tangem.common.services.secure.SecureStorage
import com.tangem.crypto.secureCompare
import com.tangem.operations.preflightread.PreflightReadFilter
import com.tangem.operations.read.ReadCommand
import com.tangem.operations.read.ReadMasterSecretCommand
import com.tangem.operations.read.ReadWalletsListCommand

/**
 * Mode for preflight read task
 * Note: Valid for cards with COS v. 4.0 and higher. Older card will always read the card and the wallet info.
 * `fullCardRead` will be used by default
 */
sealed class PreflightReadMode {

    /**
     * No card will be read at session start. `SessionEnvironment.card` will be empty
     */
    object None : PreflightReadMode()

    /**
     * Read only card info without wallet info. Valid for cards with COS v. 4.0 and higher.
     * Older card will always read card and wallet info
     */
    object ReadCardOnly : PreflightReadMode()

    /**
     * Read card info and all wallets, plus any requested extras. Used by default
     */
    data class FullCardRead(val options: Set<Option> = emptySet()) : PreflightReadMode() {

        /**
         * Optional extras layered on top of a full card read
         */
        enum class Option {
            /**
             * Show an alert and request the access code if this card is unknown yet
             */
            AccessCodeCheck,

            /**
             * Read the card's master secret. COS v8+
             */
            ReadMasterSecret,

            /**
             * Calculate and verify the backup hash. Needs the master secret, so implies [ReadMasterSecret]. COS v8+
             */
            VerifyBackup,
        }
    }

    override fun toString(): String = this::class.java.simpleName
}

class PreflightReadTask(
    private val readMode: PreflightReadMode,
    private val filter: PreflightReadFilter? = null,
    secureStorage: SecureStorage,
) : CardSessionRunnable<Card> {

    private val trustedCardsRepo = TrustedCardsRepo(secureStorage, MoshiJsonConverter.INSTANCE)

    override fun run(session: CardSession, callback: CompletionCallback<Card>) {
        Log.command(this) { " [mode - $readMode]" }

        ReadCommand().run(session) readCommand@{ result ->
            when (result) {
                is Success -> {
                    val card = result.data.card

                    try {
                        session.environment.config.filter.verifyCard(result.data.card)

                        if (session.environment.config.handleErrors) {
                            filter?.onCardRead(card = card, environment = session.environment)
                        }
                    } catch (error: TangemSdkError) {
                        callback(Failure(error))
                        return@readCommand
                    }

                    updateEnvironmentIfNeeded(card = card, session = session)
                    session.updateUserCodeIfNeeded()
                    session.fetchAccessTokensIfNeeded()
                    finalizeRead(session = session, card = card, callback = callback)
                }

                is Failure -> callback(Failure(result.error))
            }
        }
    }

    private fun finalizeRead(session: CardSession, card: Card, callback: CompletionCallback<Card>) {
        if (card.firmwareVersion < FirmwareVersion.MultiWalletAvailable) {
            val result = try {
                filterOnReadWalletsList(card, session)
                Success(card)
            } catch (error: TangemSdkError) {
                Failure(error)
            }

            callback(result)
            return
        }

        when (readMode) {
            is PreflightReadMode.FullCardRead -> fullCardRead(
                session = session,
                card = card,
                options = readMode.options,
                callback = callback,
            )
            PreflightReadMode.ReadCardOnly, PreflightReadMode.None -> callback(Success(card))
        }
    }

    private fun fullCardRead(
        session: CardSession,
        card: Card,
        options: Set<PreflightReadMode.FullCardRead.Option>,
        callback: CompletionCallback<Card>,
    ) {
        val shouldCheckAccessCode = PreflightReadMode.FullCardRead.Option.AccessCodeCheck in options
        if (!shouldCheckAccessCode || !card.isAccessCodeSet) {
            readWalletsList(session, options, callback)
            return
        }

        val cardPublicKey = session.environment.card?.cardPublicKey ?: run {
            callback(Failure(TangemSdkError.MissingPreflightRead()))
            return
        }
        if (trustedCardsRepo.attestation(cardPublicKey) != null) {
            readWalletsList(session, options, callback)
            return
        }

        session.pause()

        val showWelcomeBackWarning = card.firmwareVersion.type != FirmwareVersion.FirmwareType.Sdk

        session.environment.accessCode = UserCode(UserCodeType.AccessCode, value = null)
        session.requestUserCodeIfNeeded(
            type = UserCodeType.AccessCode,
            isFirstAttempt = true,
            showWelcomeBackWarning = showWelcomeBackWarning,
        ) { result ->
            when (result) {
                is Success -> {
                    session.resume { readWalletsList(session, options, callback) }
                }
                is Failure -> {
                    session.releaseTag()
                    callback(Failure(result.error))
                }
            }
        }
    }

    private fun readWalletsList(
        session: CardSession,
        options: Set<PreflightReadMode.FullCardRead.Option>,
        callback: CompletionCallback<Card>,
    ) {
        ReadWalletsListCommand().run(session) { result ->
            when (result) {
                is Success -> {
                    val card = session.environment.card.guard {
                        callback(Failure(TangemSdkError.MissingPreflightRead()))
                        return@run
                    }

                    try {
                        filterOnReadWalletsList(card, session)
                    } catch (error: TangemSdkError) {
                        callback(Failure(error))
                        return@run
                    }

                    val shouldReadMasterSecret = PreflightReadMode.FullCardRead.Option.ReadMasterSecret in options ||
                        PreflightReadMode.FullCardRead.Option.VerifyBackup in options
                    if (card.firmwareVersion >= FirmwareVersion.v8 && shouldReadMasterSecret) {
                        readMasterSecret(session) { masterSecretResult ->
                            when (masterSecretResult) {
                                is Success -> {
                                    if (PreflightReadMode.FullCardRead.Option.VerifyBackup in options) {
                                        verifyBackup(result.data.backupHash, session)
                                    }
                                    callback(Success(session.environment.card ?: masterSecretResult.data))
                                }
                                is Failure -> callback(masterSecretResult)
                            }
                        }
                    } else {
                        callback(Success(card))
                    }
                }

                is Failure -> callback(Failure(result.error))
            }
        }
    }

    private fun readMasterSecret(session: CardSession, callback: CompletionCallback<Card>) {
        ReadMasterSecretCommand().run(session) { result ->
            when (result) {
                is Success -> {
                    val card = session.environment.card.guard {
                        callback(Failure(TangemSdkError.MissingPreflightRead()))
                        return@run
                    }

                    session.environment.card = card.copy(masterSecret = result.data.masterSecret)
                    callback(Success(session.environment.card ?: card))
                }
                is Failure -> callback(Failure(result.error))
            }
        }
    }

    @Suppress("MagicNumber")
    private fun verifyBackup(backupHash: ByteArray?, session: CardSession) {
        if (backupHash == null) return
        val card = session.environment.card ?: return

        // No backup yet - all zeros
        if (backupHash.all { it == 0.toByte() }) return

        val calculatedHash = calculateBackupHash(card)
        val isBackupVerified = calculatedHash.secureCompare(backupHash)
        session.environment.card = card.copy(isBackupVerified = isBackupVerified)
        Log.session { "Backup is verified: $isBackupVerified" }
    }

    private fun filterOnReadWalletsList(card: Card, session: CardSession) {
        if (!session.environment.config.handleErrors) return

        filter?.onFullCardRead(card = card, environment = session.environment)
    }

    private fun updateEnvironmentIfNeeded(card: Card, session: CardSession) {
        if (FirmwareVersion.visaRange.contains(card.firmwareVersion.doubleValue)) {
            session.environment.config.cardIdDisplayFormat = CardIdDisplayFormat.None
        }
    }

    companion object {
        private const val BACKUP_HASH_PREFIX_LENGTH = 8

        @Suppress("MagicNumber")
        fun calculateBackupHash(card: Card): ByteArray {
            val hashData = mutableListOf<Byte>()
            hashData.addAll("WALLETS".toByteArray(Charsets.UTF_8).toList())

            // Master secret
            card.masterSecret?.let { masterSecret ->
                hashData.add((masterSecret.status.code and 0x7F).toByte())

                masterSecret.publicKey?.let { hashData.addAll(it.toList()) }
                masterSecret.chainCode?.let { hashData.addAll(it.toList()) }
            }

            // Wallets sorted by index
            for (wallet in card.wallets.sortedBy { it.index }) {
                hashData.add(wallet.index.toByte())
                hashData.add((wallet.status.code and 0x7F).toByte())

                wallet.publicKey?.let { hashData.addAll(it.toList()) }
                wallet.chainCode?.let { hashData.addAll(it.toList()) }
            }

            return hashData.toByteArray().calculateSha256()
                .take(BACKUP_HASH_PREFIX_LENGTH)
                .toByteArray()
        }
    }
}
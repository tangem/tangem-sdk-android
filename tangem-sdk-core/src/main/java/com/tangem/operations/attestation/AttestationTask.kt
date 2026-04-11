package com.tangem.operations.attestation

import com.tangem.common.CompletionResult
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.*
import com.tangem.common.extensions.guard
import com.tangem.common.json.MoshiJsonConverter
import com.tangem.common.services.TrustedCardsRepo
import com.tangem.common.services.secure.SecureStorage
import com.tangem.operations.attestation.api.TangemApiService
import com.tangem.operations.attestation.service.OnlineAttestationServiceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AttestationTask(
    private val mode: Mode,
    secureStorage: SecureStorage,
) : CardSessionRunnable<Attestation> {

    /**
     * If `true`, AttestationTask will not pause NFC session after all card operations complete. Useful
     * for chaining  tasks after AttestationTask. False by default
     */
    var shouldKeepSessionOpened = false

    private val trustedCardsRepo = TrustedCardsRepo(secureStorage, MoshiJsonConverter.INSTANCE)

    private var currentAttestationStatus: Attestation = Attestation.empty

    override fun run(session: CardSession, callback: CompletionCallback<Attestation>) {
        session.environment.card.guard {
            callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
            return
        }

        attestCard(session, callback)
    }

    private fun attestCard(session: CardSession, callback: CompletionCallback<Attestation>) {
        AttestCardKeyCommand().run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    // This card already attested with the current or more secured mode
                    val attestation =
                        trustedCardsRepo.attestation(session.environment.card!!.cardPublicKey)

                    if (attestation != null && attestation.mode.ordinal >= mode.ordinal) {
                        currentAttestationStatus = attestation
                        complete(session, callback)
                        return@run
                    }

                    // Continue attestation
                    currentAttestationStatus = currentAttestationStatus.copy(
                        cardKeyAttestation = Attestation.Status.VerifiedOffline,
                    )
                    continueAttestation(session, callback)
                }
                is CompletionResult.Failure -> {
                    // Card attestation failed. Update status and fail attestation
                    if (result.error is TangemSdkError.CardVerificationFailed) {
                        currentAttestationStatus = currentAttestationStatus.copy(
                            cardKeyAttestation = Attestation.Status.Failed,
                        )
                    }
                    callback(CompletionResult.Failure(result.error))
                }
            }
        }
    }

    private fun continueAttestation(session: CardSession, callback: CompletionCallback<Attestation>) {
        when (mode) {
            Mode.Offline -> complete(session, callback)
            Mode.Normal -> runOnlineAttestation(session, callback)
            Mode.Full -> runWalletsAttestation(session, callback)
        }
    }

    private fun runWalletsAttestation(session: CardSession, callback: CompletionCallback<Attestation>) {
        attestWallets(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    // Wallets attestation completed. Update status and continue attestation
                    val hasWarnings = result.data
                    val status = if (hasWarnings) Attestation.Status.Warning else Attestation.Status.Verified
                    currentAttestationStatus = currentAttestationStatus.copy(walletKeysAttestation = status)
                    runExtraAttestation(session, callback)
                }
                is CompletionResult.Failure -> {
                    // Wallets attestation failed. Update status and fail attestation
                    if (result.error is TangemSdkError.CardVerificationFailed) {
                        currentAttestationStatus = currentAttestationStatus.copy(
                            walletKeysAttestation = Attestation.Status.Failed,
                        )
                    }
                    callback(CompletionResult.Failure(result.error))
                }
            }
        }
    }

    private fun runExtraAttestation(session: CardSession, callback: CompletionCallback<Attestation>) {
        // TODO: ATTEST_CARD_FIRMWARE, ATTEST_CARD_UNIQUENESS
        runOnlineAttestation(session, callback)
    }

    private fun attestWallets(session: CardSession, callback: CompletionCallback<Boolean>) {
        val card = session.environment.card.guard {
            callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
            return
        }

        card.assertWalletsAccess()?.let { error ->
            callback(CompletionResult.Failure(error))
            return
        }

        val attestationCommands = card.wallets.mapNotNull { wallet ->
            wallet.publicKey?.let { AttestWalletKeyTask(it) }
        }

        if (attestationCommands.isEmpty()) {
            callback(CompletionResult.Success(false))
            return
        }

        val hasWarnings = card.wallets.mapNotNull { it.totalSignedHashes }.any { it > MAX_COUNTER }

        attestWallet(attestationCommands, commandIndex = 0, hasWarnings = hasWarnings, session, callback)
    }

    private fun attestWallet(
        attestationCommands: List<AttestWalletKeyTask>,
        commandIndex: Int,
        hasWarnings: Boolean,
        session: CardSession,
        callback: CompletionCallback<Boolean>,
    ) {
        if (commandIndex == attestationCommands.size) {
            callback(CompletionResult.Success(hasWarnings))
            return
        }

        attestationCommands[commandIndex].run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    // check for hacking attempts with attestWallet
                    val shouldWarn = result.data.counter?.let { it > MAX_COUNTER } ?: false

                    attestWallet(
                        attestationCommands,
                        commandIndex = commandIndex + 1,
                        hasWarnings = if (shouldWarn) true else hasWarnings,
                        session,
                        callback,
                    )
                }
                is CompletionResult.Failure -> {
                    callback(CompletionResult.Failure(result.error))
                }
            }
        }
    }

    private fun runOnlineAttestation(session: CardSession, callback: CompletionCallback<Attestation>) {
        val card = session.environment.card.guard {
            callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
            return
        }

        val factory = OnlineAttestationServiceFactory(
            tangemApiService = TangemApiService { session.environment.config.tangemApiBaseUrl },
            secureStorage = session.environment.secureStorage,
        )
        val onlineAttestationService = factory.create(card)

        if (!shouldKeepSessionOpened) {
            session.pause()
        }

        session.scope.launch(Dispatchers.IO) {
            val attestResult = onlineAttestationService.attestCard(
                cardId = card.cardId,
                cardPublicKey = card.cardPublicKey,
            )

            when (attestResult) {
                is CompletionResult.Success -> {
                    currentAttestationStatus = currentAttestationStatus.copy(
                        cardKeyAttestation = Attestation.Status.Verified,
                    )
                    trustedCardsRepo.append(
                        session.environment.card!!.cardPublicKey,
                        currentAttestationStatus,
                    )
                }
                is CompletionResult.Failure -> {
                    if (attestResult.error is TangemSdkError.CardVerificationFailed) {
                        currentAttestationStatus = currentAttestationStatus.copy(
                            cardKeyAttestation = Attestation.Status.Failed,
                        )
                    }
                }
            }

            launch(Dispatchers.Main) {
                processAttestationReport(session, callback)
            }
        }
    }

    private fun retryOnline(session: CardSession, callback: CompletionCallback<Attestation>) {
        runOnlineAttestation(session, callback)
    }

    private fun processAttestationReport(session: CardSession, callback: CompletionCallback<Attestation>) {
        when (currentAttestationStatus.status) {
            Attestation.Status.Failed,
            Attestation.Status.Skipped,
            -> {
                val isDevelopmentCard = session.environment.card!!.firmwareVersion.type ==
                    FirmwareVersion.FirmwareType.Sdk

                if (isDevelopmentCard) {
                    session.viewDelegate.attestationDidFail(
                        isDevCard = true,
                        positive = { complete(session, callback) },
                        negative = { callback(CompletionResult.Failure(TangemSdkError.UserCancelled())) },
                    )
                } else {
                    callback(CompletionResult.Failure(TangemSdkError.CardVerificationFailed()))
                }
            }
            Attestation.Status.Verified -> complete(session, callback)
            Attestation.Status.VerifiedOffline -> {
                if (session.environment.config.attestationMode == Mode.Offline) {
                    complete(session, callback)
                    return
                }

                session.viewDelegate.attestationCompletedOffline(
                    positive = { complete(session, callback) },
                    negative = { callback(CompletionResult.Failure(TangemSdkError.UserCancelled())) },
                    retry = { retryOnline(session, callback) },
                )
            }
            Attestation.Status.Warning -> {
                session.viewDelegate.attestationCompletedWithWarnings {
                    complete(session, callback)
                }
            }
        }
    }

    private fun complete(session: CardSession, callback: CompletionCallback<Attestation>) {
        session.environment.card = session.environment.card?.copy(attestation = currentAttestationStatus)
        callback(CompletionResult.Success(currentAttestationStatus))
    }

    enum class Mode {
        Offline,
        Normal,
        Full,
    }

    companion object {
        /**
         * Attest wallet count or sign command count greater this value is looks suspicious.
         */
        private const val MAX_COUNTER = 100000
    }
}
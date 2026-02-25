package com.tangem.operations

import com.tangem.Log
import com.tangem.common.CompletionResult.Failure
import com.tangem.common.CompletionResult.Success
import com.tangem.common.UserCodeType
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.*
import com.tangem.common.extensions.guard
import com.tangem.common.json.MoshiJsonConverter
import com.tangem.common.services.TrustedCardsRepo
import com.tangem.common.services.secure.SecureStorage
import com.tangem.operations.preflightread.PreflightReadFilter
import com.tangem.operations.read.ReadCommand
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
     * Read card info and all wallets. Used by default
     */
    object FullCardRead : PreflightReadMode()

    /**
     * Read card info and all wallets. Show alert if this card is unknown yet
     */
    object FullCardReadWithAccessCodeCheck : PreflightReadMode()

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
            PreflightReadMode.FullCardRead -> readWalletsList(session, callback)
            PreflightReadMode.FullCardReadWithAccessCodeCheck -> checkFullCardReadWithAccessCodeCheckMode(
                session = session,
                card = card,
                callback = callback,
            )
            PreflightReadMode.ReadCardOnly, PreflightReadMode.None -> callback(Success(card))
        }
    }

    private fun checkFullCardReadWithAccessCodeCheckMode(
        session: CardSession,
        card: Card,
        callback: CompletionCallback<Card>,
    ) {
        val cardPublicKey = session.environment.card?.cardPublicKey ?: run {
            callback(Failure(TangemSdkError.MissingPreflightRead()))
            return
        }
        val attestationResult = trustedCardsRepo.attestation(cardPublicKey)
        if (card.isAccessCodeSet && attestationResult == null) {
            session.pause()

            session.requestUserCodeIfNeeded(
                type = UserCodeType.AccessCode,
                isFirstAttempt = true,
                showWelcomeBackWarning = true,
            ) { result ->
                when (result) {
                    is Success -> {
                        session.resume { readWalletsList(session, callback) }
                    }
                    is Failure -> {
                        if (result.error is TangemSdkError.UserCancelled) {
                            session.viewDelegate.dismiss()
                        } else {
                            callback(Failure(result.error))
                        }
                    }
                }
            }
        } else {
            readWalletsList(session, callback)
        }
    }

    private fun readWalletsList(session: CardSession, callback: CompletionCallback<Card>) {
        ReadWalletsListCommand().run(session) { result ->
            when (result) {
                is Success -> {
                    val card = session.environment.card.guard {
                        callback(Failure(TangemSdkError.MissingPreflightRead()))
                        return@run
                    }

                    val callbackResult = try {
                        filterOnReadWalletsList(card, session)
                        Success(card)
                    } catch (error: TangemSdkError) {
                        Failure(error)
                    }

                    callback(callbackResult)
                }

                is Failure -> callback(Failure(result.error))
            }
        }
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
}
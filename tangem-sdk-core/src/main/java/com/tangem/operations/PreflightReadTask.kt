package com.tangem.operations

import com.tangem.Log
import com.tangem.common.CardTokens
import com.tangem.common.CompletionResult
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.*
import com.tangem.common.doOnFailure
import com.tangem.common.extensions.guard
import com.tangem.operations.preflightread.PreflightReadFilter
import com.tangem.operations.read.ReadCommand
import com.tangem.operations.read.ReadMasterSecretCommand
import com.tangem.operations.read.ReadWalletsListCommand
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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

    override fun toString(): String = this::class.java.simpleName
}

class PreflightReadTask(
    private val readMode: PreflightReadMode,
    private val filter: PreflightReadFilter? = null,
) : CardSessionRunnable<Card> {

    override fun run(session: CardSession, callback: CompletionCallback<Card>) {
        Log.command(this) { " [mode - $readMode]" }

        ReadCommand().run(session) readCommand@{ result ->
            when (result) {
                is CompletionResult.Success -> {
                    val card = result.data.card

                    try {
                        session.environment.config.filter.verifyCard(result.data.card)

                        if (session.environment.config.handleErrors) {
                            filter?.onCardRead(card = card, environment = session.environment)
                        }
                    } catch (error: TangemSdkError) {
                        callback(CompletionResult.Failure(error))
                        return@readCommand
                    }

                    updateEnvironmentIfNeeded(card = card, session = session)
                    session.updateUserCodeIfNeeded()
                    session.updateAccessTokensIfNeeded()
                    if (FirmwareVersion.v7.doubleValue <= result.data.card.firmwareVersion.doubleValue) {
                        var hasCardTokens = session.environment.cardTokens != null
                        if (hasCardTokens) {
                            val resultEstablishEncryption = runBlocking(session.scope.coroutineContext) {
                                return@runBlocking session.establishEncryptionWithAccessToken()
                            }
                            resultEstablishEncryption.doOnFailure {
                                if (it is TangemSdkError.InvalidAccessTokens || it is TangemSdkError.InvalidState) {
                                    hasCardTokens = false
                                } else {
                                    callback(CompletionResult.Failure(it))
                                    return@readCommand
                                }
                            }
                        }
                        if (!hasCardTokens) {
                            val resultEstablishEncryption = runBlocking(session.scope.coroutineContext) {
                                return@runBlocking session.establishEncryptionWithSecurityDelay()
                            }
                            resultEstablishEncryption.doOnFailure {
                                callback(CompletionResult.Failure(it))
                                return@readCommand
                            }
                        }
                        if (!hasCardTokens && (session.environment.card?.settings?.isBackupRequired == false || session.environment.card?.backupStatus?.isActive == true)) {
                            val readTokensResult = runBlocking(session.scope.coroutineContext) {
                                return@runBlocking readTokens(session, renew = false)
                            }
                            readTokensResult.doOnFailure {
                                callback(CompletionResult.Failure(it))
                                return@readCommand
                            }
                        }
                    }
                    finalizeRead(session = session, card = card, callback = callback)
                }

                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }
        }
    }

    private fun finalizeRead(session: CardSession, card: Card, callback: CompletionCallback<Card>) {
        if (card.firmwareVersion < FirmwareVersion.MultiWalletAvailable) {
            val result = try {
                filterOnReadWalletsList(card, session)
                CompletionResult.Success(card)
            } catch (error: TangemSdkError) {
                CompletionResult.Failure(error)
            }

            callback(result)
            return
        }

        when (readMode) {
            PreflightReadMode.FullCardRead -> readWalletsList(session) { result ->
                if (card.firmwareVersion < FirmwareVersion.MasterSecretAvailable ) {
                    callback(result)
                }
                readMasterSecret(session)
                { result ->
                    val callbackData = when (result) {
                        is CompletionResult.Success -> {
                            CompletionResult.Success(result.data)
                        }
                        is CompletionResult.Failure -> {
                            CompletionResult.Failure(result.error)
                        }
                    }
                    callback(callbackData)
                }
            }
            PreflightReadMode.ReadCardOnly, PreflightReadMode.None -> callback(CompletionResult.Success(card))
        }
    }

    private suspend fun readTokens(session: CardSession, renew: Boolean): CompletionResult<Boolean> {
        Log.session { "read tokens, renew: $renew" }

        val result = suspendCoroutine { continuation ->
            ManageAccessTokensCommand(if (renew) ManageAccessTokensMode.Renew else ManageAccessTokensMode.Get)
                .also { Log.command(it) }
                .run(session) { continuation.resume(it) }
        }
        return when (result) {
            is CompletionResult.Success -> {
                if (!result.data.accessToken.contentEquals(ByteArray(32)) && !result.data.identifyToken.contentEquals(
                        ByteArray(32)
                    )
                ) {
                    // zero tokens - there are no tokens on card (need renew)
                    session.environment.cardTokens = CardTokens(result.data.accessToken, result.data.identifyToken)
                    // session.updateAccessTokensIfNeeded()
                    CompletionResult.Success(true)
                } else if (!renew) {
                    readTokens(session, renew = true)
                } else {
                    CompletionResult.Failure(TangemSdkError.InvalidResponse())
                }
            }

            is CompletionResult.Failure -> CompletionResult.Failure(result.error)
        }
    }

    private fun readMasterSecret(session: CardSession, callback: CompletionCallback<Card>) {

        ReadMasterSecretCommand()
            .also { Log.command(it) { "ReadMasterSecret" } }
            .run(session) { result ->
                when (result) {
                    is CompletionResult.Success -> {
                        val card = session.environment.card.guard {
                            callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
                            return@run
                        }
                        try {
                            session.environment.card = card.setMasterSecret(result.data.masterSecret)
                            callback(CompletionResult.Success(card))
                        } catch (error: TangemSdkError) {
                            callback(CompletionResult.Failure(error))
                        }
                    }
                    is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                }
            }
    }

    private fun readWalletsList(session: CardSession, callback: CompletionCallback<Card>) {
        ReadWalletsListCommand().run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    val card = session.environment.card.guard {
                        callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
                        return@run
                    }

                    val callbackResult = try {
                        filterOnReadWalletsList(card, session)
                        CompletionResult.Success(card)
                    } catch (error: TangemSdkError) {
                        CompletionResult.Failure(error)
                    }

                    callback(callbackResult)
                }

                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
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
package com.tangem.operations.securechannel.establish

import com.tangem.Log
import com.tangem.common.CompletionResult
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionEncryption
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.TangemError
import com.tangem.common.core.TangemSdkError
import com.tangem.common.encryption.EncryptionMode
import com.tangem.common.extensions.calculateSha256
import com.tangem.crypto.StrongEncryptionHelper
import com.tangem.operations.PreflightReadMode

/**
 * Orchestrates secure channel establishment using ECDH key exchange for v8+ cards.
 * Uses security delay-based authorization with card attestation verification.
 */
class EstablishSecureChannelWithSecurityDelayTask : CardSessionRunnable<Unit> {

    private var handshakeRetryCount = 0

    override val cardSessionEncryption: CardSessionEncryption
        get() = CardSessionEncryption.NONE

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.None

    override fun run(session: CardSession, callback: CompletionCallback<Unit>) {
        AuthorizeWithSecurityDelayCommand().run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    completeEstablishment(
                        authorizeResponse = result.data,
                        session = session,
                        callback = callback,
                    )
                }
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }
        }
    }

    private fun completeEstablishment(
        authorizeResponse: AuthorizeWithSecurityDelayResponse,
        session: CardSession,
        callback: CompletionCallback<Unit>,
    ) {
        try {
            val encryptionHelper = StrongEncryptionHelper()
            val sessionKeyB = encryptionHelper.keyA

            OpenSessionWithSecurityDelayCommand(sessionKeyB = sessionKeyB).run(session) { result ->
                when (result) {
                    is CompletionResult.Success -> {
                        try {
                            val ecdhSecret = encryptionHelper.generateSecret(authorizeResponse.pubSessionKeyA)
                            val sessionKey = ecdhSecret.calculateSha256()
                            ecdhSecret.fill(0)
                            session.environment.encryptionMode = EncryptionMode.CcmWithSecurityDelay
                            session.environment.encryptionKey = sessionKey
                            session.secureChannelSession?.didEstablishChannel(result.data.accessLevel)

                            Log.session { "Secure channel established with security delay" }
                            callback(CompletionResult.Success(Unit))
                        } catch (error: Throwable) {
                            callback(CompletionResult.Failure(error.toTangemSdkError()))
                        }
                    }
                    is CompletionResult.Failure -> {
                        if (shouldRestartHandshake(result.error)) {
                            handshakeRetryCount++
                            Log.session {
                                "Card lost handshake state (${result.error})," +
                                    " restarting secure channel establishment, attempt $handshakeRetryCount"
                            }
                            session.secureChannelSession?.reset()
                            run(session, callback)
                        } else {
                            callback(CompletionResult.Failure(result.error))
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            callback(CompletionResult.Failure(error.toTangemSdkError()))
        }
    }

    /**
     * If the security delay lasts longer than the platform transceive timeout, the tag is reported as lost

     * The re-sent [OpenSessionWithSecurityDelayCommand] then fails with [TangemSdkError.InvalidState].
     * The remaining delay persists on the card, so each restart converges to a successful handshake.
     */
    private fun shouldRestartHandshake(error: TangemError): Boolean {
        val isHandshakeStateLost = error is TangemSdkError.InvalidState || error is TangemSdkError.AccessDenied
        return isHandshakeStateLost && handshakeRetryCount < MAX_HANDSHAKE_RETRIES
    }

    private companion object {
        const val MAX_HANDSHAKE_RETRIES = 10
    }
}

private fun Throwable.toTangemSdkError(): com.tangem.common.core.TangemSdkError {
    return if (this is com.tangem.common.core.TangemSdkError) {
        this
    } else {
        com.tangem.common.core.TangemSdkError.CryptoUtilsError(message ?: "Unknown error")
    }
}
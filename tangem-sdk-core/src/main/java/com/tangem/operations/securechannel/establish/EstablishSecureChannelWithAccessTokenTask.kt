package com.tangem.operations.securechannel.establish

import com.tangem.Log
import com.tangem.common.CompletionResult
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionEncryption
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.TangemSdkError
import com.tangem.common.encryption.EncryptionMode
import com.tangem.crypto.CryptoUtils
import com.tangem.crypto.pbkdf2Hash
import com.tangem.operations.PreflightReadMode

/**
 * Orchestrates secure channel establishment using access tokens for v8+ cards.
 * Performs challenge-response with HMAC-based attestation, then derives a session key.
 */
class EstablishSecureChannelWithAccessTokenTask : CardSessionRunnable<Unit> {

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.None

    override val cardSessionEncryption: CardSessionEncryption
        get() = CardSessionEncryption.NONE

    override fun run(session: CardSession, callback: CompletionCallback<Unit>) {
        AuthorizeWithAccessTokensCommand().run(session) { result ->
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

    @Suppress("MagicNumber")
    private fun completeEstablishment(
        authorizeResponse: AuthorizeWithAccessTokenResponse,
        session: CardSession,
        callback: CompletionCallback<Unit>,
    ) {
        try {
            val accessTokens = session.environment.cardAccessTokens
                ?: throw TangemSdkError.MissingAccessTokens()

            val challengeB = CryptoUtils.generateRandomBytes(length = 32)
            val accessKey = xorBytes(accessTokens.accessToken, authorizeResponse.challengeA)
            val input = "SESSION.TERM".toByteArray(Charsets.UTF_8) +
                authorizeResponse.challengeA +
                challengeB
            val hmacAttestB = hmacSha256(accessKey, input)
            val salt = accessTokens.identifyToken + authorizeResponse.challengeA + challengeB
            val sessionKey = accessKey.pbkdf2Hash(salt, iterations = 5)

            OpenSessionWithAccessTokenCommand(
                challengeB = challengeB,
                hmacAttestB = hmacAttestB,
                sessionKey = sessionKey,
            ).run(session) { result ->
                when (result) {
                    is CompletionResult.Success -> {
                        session.environment.encryptionMode = EncryptionMode.CcmWithAccessToken
                        session.environment.encryptionKey = sessionKey
                        session.secureChannelSession?.didEstablishChannel(result.data.accessLevel)

                        Log.session { "Secure channel established with access tokens" }
                        callback(CompletionResult.Success(Unit))
                    }
                    is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                }
            }
        } catch (error: Throwable) {
            val sdkError = if (error is TangemSdkError) error
            else TangemSdkError.CryptoUtilsError(error.message ?: "Unknown error")
            callback(CompletionResult.Failure(sdkError))
        }
    }

    private fun xorBytes(a: ByteArray, b: ByteArray): ByteArray {
        require(a.size == b.size) { "Arrays must have the same size for XOR" }
        return ByteArray(a.size) { i -> (a[i].toInt() xor b[i].toInt()).toByte() }
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
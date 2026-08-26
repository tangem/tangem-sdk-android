package com.tangem.operations.securechannel.authorize

import com.tangem.Log
import com.tangem.common.CompletionResult
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.CompletionCallback
import com.tangem.operations.PreflightReadMode

/**
 * Orchestrates PIN challenge-response authorization for v8+ cards.
 * Sends a challenge request, then responds with HMAC of the access code.
 */
class AuthorizeWithPINTask : CardSessionRunnable<Unit> {

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.None

    override fun run(session: CardSession, callback: CompletionCallback<Unit>) {
        Log.session { "authorizeWithPin" }

        AuthorizeWithPinChallengeCommand().run(session) { challengeResult ->
            when (challengeResult) {
                is CompletionResult.Success -> {
                    AuthorizeWithPinResponseCommand(
                        challengeWithXor = challengeResult.data.challengeWithXor,
                    ).run(session) { responseResult ->
                        when (responseResult) {
                            is CompletionResult.Success -> {
                                session.secureChannelSession?.didAuthorizePin(
                                    responseResult.data.accessLevel,
                                )
                                callback(CompletionResult.Success(Unit))
                            }
                            is CompletionResult.Failure -> {
                                callback(CompletionResult.Failure(responseResult.error))
                            }
                        }
                    }
                }
                is CompletionResult.Failure -> {
                    callback(CompletionResult.Failure(challengeResult.error))
                }
            }
        }
    }
}
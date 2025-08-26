package com.tangem.operations

import com.tangem.*
import com.tangem.common.CompletionResult
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.*
import com.tangem.common.extensions.guard

/**
 * Task that authorize with PIN for v7+ firmware.
 */
class AuthorizeWithPinTask(
    override val allowsRequestAccessCodeFromRepository: Boolean = false,
) : CardSessionRunnable<Boolean> {

    override fun run(session: CardSession, callback: CompletionCallback<Boolean>) {
        val card = session.environment.card.guard {
            callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
            return
        }

        @Suppress("MagicNumber")
        if (card.firmwareVersion > FirmwareVersion.v7
        ) {
            authorizeWithPin(session, callback)
        } else {
            callback(CompletionResult.Success(true))
        }
    }

    private fun authorizeWithPin(session: CardSession, callback: CompletionCallback<Boolean>) {
        Log.session { "authorizeWithPin" }
        AuthorizeWithPinChallengeCommand().run(session) { response ->
            when (response) {
                is CompletionResult.Success -> {
                    AuthorizeWithPinResponseCommand(response.data.challenge).run(session) { result ->
                        when (result) {
                            is CompletionResult.Success -> {
                                session.environment.accessLevel = result.data.accessLevel
                                session.environment.isPinChecked = true
                                callback(CompletionResult.Success(true))
                            }
                            is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                        }

                    }
                }
                is CompletionResult.Failure -> callback(CompletionResult.Failure(response.error))
            }
        }
    }
}
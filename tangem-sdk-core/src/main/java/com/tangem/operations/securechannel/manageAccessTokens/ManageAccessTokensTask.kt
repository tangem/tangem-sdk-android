package com.tangem.operations.securechannel.manageAccessTokens

import com.tangem.common.CompletionResult
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.CompletionCallback

class ManageAccessTokensTask : CardSessionRunnable<Unit> {

    override fun run(
        session: CardSession,
        callback: CompletionCallback<Unit>,
    ) {
        val getCommand = ManageAccessTokensCommand(ManageAccessTokensMode.GET)
        getCommand.run(session) { result ->
            when (result) {
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                is CompletionResult.Success -> {
                    if (result.data.isZeroResponse()) {
                        val renewCommand = ManageAccessTokensCommand(ManageAccessTokensMode.RENEW)
                        renewCommand.run(session) { renewResult ->
                            when (renewResult) {
                                is CompletionResult.Failure -> callback(CompletionResult.Failure(renewResult.error))
                                is CompletionResult.Success -> callback(CompletionResult.Success(Unit))
                            }
                        }
                    } else {
                        callback(CompletionResult.Success(Unit))
                    }
                }
            }
        }
    }
}
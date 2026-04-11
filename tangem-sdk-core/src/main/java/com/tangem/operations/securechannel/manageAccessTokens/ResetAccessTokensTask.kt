package com.tangem.operations.securechannel.manageAccessTokens

import com.tangem.Log
import com.tangem.common.CompletionResult
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.CompletionCallback
import com.tangem.operations.PreflightReadMode

/**
 * Reset access tokens on the card.
 */
class ResetAccessTokensTask : CardSessionRunnable<Unit> {

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.ReadCardOnly

    override fun run(session: CardSession, callback: CompletionCallback<Unit>) {
        ManageAccessTokensCommand(mode = ManageAccessTokensMode.RESET)
            .run(session) { result ->
                when (result) {
                    is CompletionResult.Success -> callback(CompletionResult.Success(Unit))
                    is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                }
            }
    }
}
package com.tangem.operations.resetcode

import com.tangem.common.CompletionResult
import com.tangem.common.SuccessResponse
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.CompletionCallback
import com.tangem.operations.PreflightReadMode
import com.tangem.operations.securechannel.manageAccessTokens.ManageAccessTokensCommand
import com.tangem.operations.securechannel.manageAccessTokens.ManageAccessTokensMode

class ResetPinTask(
    private val confirmationCard: ConfirmationCard,
    private val accessCode: ByteArray,
    private val passcode: ByteArray,
) : CardSessionRunnable<SuccessResponse> {

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.ReadCardOnly

    override val shouldAskForAccessCode: Boolean
        get() = false

    override fun run(session: CardSession, callback: CompletionCallback<SuccessResponse>) {
        AuthorizeResetPinTokenCommand(confirmationCard).run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    ResetPinCommand(accessCode, passcode).run(session) { resetResult ->
                        when (resetResult) {
                            is CompletionResult.Success -> {
                                val card = session.environment.card
                                if (card != null && card.firmwareVersion < FirmwareVersion.v8) {
                                    callback(CompletionResult.Success(resetResult.data))
                                    return@run
                                }

                                ManageAccessTokensCommand(ManageAccessTokensMode.RENEW).run(session) {
                                    callback(CompletionResult.Success(resetResult.data))
                                }
                            }
                            is CompletionResult.Failure -> callback(CompletionResult.Failure(resetResult.error))
                        }
                    }
                }
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }
        }
    }
}
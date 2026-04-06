package com.tangem.operations.usersetttings

import com.tangem.Log
import com.tangem.common.CompletionResult
import com.tangem.common.SuccessResponse
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.TangemSdkError
import com.tangem.operations.PreflightReadMode

/**
 * Task to enable or disable the NDEF reading feature on the card.
 *
 * @param isDisabled Whether NDEF reading feature is disabled on the card.
 */
class SetNDEFDisabledTask(private val isDisabled: Boolean) : CardSessionRunnable<SuccessResponse> {

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.ReadCardOnly

    override fun run(session: CardSession, callback: CompletionCallback<SuccessResponse>) {
        val card = session.environment.card
        if (card == null) {
            callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
            return
        }

        if (card.firmwareVersion < FirmwareVersion.v8) {
            callback(CompletionResult.Failure(TangemSdkError.NotSupportedFirmwareVersion()))
            return
        }

        val userSettings = card.userSettings.copy(isNDEFDisabled = isDisabled)
        val setUserSettingsCommand = SetUserSettingsCommand(userSettings)
        setUserSettingsCommand.run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    callback(CompletionResult.Success(SuccessResponse(cardId = result.data.cardId)))
                }
                is CompletionResult.Failure -> {
                    callback(CompletionResult.Failure(result.error))
                }
            }
        }
    }

    protected fun finalize() {
        Log.debug { "SetNDEFDisabledTask finalize" }
    }
}
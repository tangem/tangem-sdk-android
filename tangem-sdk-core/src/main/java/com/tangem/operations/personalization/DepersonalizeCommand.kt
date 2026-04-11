package com.tangem.operations.personalization

import com.squareup.moshi.JsonClass
import com.tangem.Log
import com.tangem.common.CompletionResult
import com.tangem.common.UserCode
import com.tangem.common.UserCodeType
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionEncryption
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.SessionEnvironment
import com.tangem.operations.Command
import com.tangem.operations.CommandResponse
import com.tangem.operations.PreflightReadMode

@JsonClass(generateAdapter = true)
data class DepersonalizeResponse(
    val success: Boolean,
) : CommandResponse

/**
 * Command available on SDK cards only
 *
 * This command resets card to initial state,
 * erasing all data written during personalization and usage.
 */
class DepersonalizeCommand : Command<DepersonalizeResponse>() {

    override val cardSessionEncryption: CardSessionEncryption = CardSessionEncryption.NONE
    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.None

    override fun run(session: CardSession, callback: CompletionCallback<DepersonalizeResponse>) {
        Log.command(this)
        transceive(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    if (result.data.success) {
                        session.environment.accessCode = UserCode(UserCodeType.AccessCode, null)
                        session.environment.passcode = UserCode(UserCodeType.Passcode, null)
                        session.resetAccessTokens()
                    }
                    callback(CompletionResult.Success(result.data))
                }
                is CompletionResult.Failure -> {
                    callback(CompletionResult.Failure(result.error))
                }
            }
        }
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        return CommandApdu(Instruction.Depersonalize, byteArrayOf())
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): DepersonalizeResponse {
        return DepersonalizeResponse(true)
    }
}
package com.tangem.commands

import com.tangem.CardSession
import com.tangem.SessionEnvironment
import com.tangem.TangemSdkError
import com.tangem.common.CompletionResult
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvTag

class CheckPinResponse(
        val isPin2Default: Boolean
) : CommandResponse

class CheckPinCommand : Command<CheckPinResponse>() {

    override val requiresPin2 = true

    override fun run(session: CardSession, callback: (result: CompletionResult<CheckPinResponse>) -> Unit) {
        super.run(session) { result ->
            when (result) {
                is CompletionResult.Success -> callback(result)
                is CompletionResult.Failure -> {
                    if (result.error is TangemSdkError.InvalidParams) {
                        callback(CompletionResult.Success(CheckPinResponse(false)))
                    } else {
                        callback(result)
                    }
                }
            }
        }
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = TlvBuilder()
        tlvBuilder.append(TlvTag.CardId, environment.card?.cardId)
        tlvBuilder.append(TlvTag.Pin, environment.pin1?.value)
        tlvBuilder.append(TlvTag.Pin2, environment.pin2?.value)
        tlvBuilder.append(TlvTag.Cvc, environment.cvc)
        tlvBuilder.append(TlvTag.NewPin, environment.pin1?.value)
        tlvBuilder.append(TlvTag.NewPin2, environment.pin2?.value)
        return CommandApdu(Instruction.SetPin, tlvBuilder.serialize())
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): CheckPinResponse {
        val tlvData = apdu.getTlvData() ?: throw TangemSdkError.DeserializeApduFailed()
        return CheckPinResponse(
                isPin2Default = true
        )
    }

}
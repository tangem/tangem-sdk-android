package com.tangem.commands.read

import com.tangem.CardSession
import com.tangem.SessionEnvironment
import com.tangem.TangemError
import com.tangem.TangemSdkError
import com.tangem.commands.Command
import com.tangem.commands.common.card.Card
import com.tangem.commands.common.card.CardDeserializer
import com.tangem.common.CompletionResult
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvTag

/**
 * This command receives from the Tangem Card all the data about the card and the wallet,
 * including unique card number (CID or cardId) that has to be submitted while calling all other commands.
 */
class ReadCommand : Command<Card>() {

    override fun run(session: CardSession, callback: (result: CompletionResult<Card>) -> Unit) {
        super.run(session) {
            when (it) {
                is CompletionResult.Success -> {
                    session.environment.card = it.data
                    callback(it)
                }
                is CompletionResult.Failure -> callback(CompletionResult.Failure(it.error))
            }
        }
    }

    override fun mapError(card: Card?, error: TangemError): TangemError {
        if (error is TangemSdkError.InvalidParams) {
            return TangemSdkError.Pin1Required()
        }
        return error
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = TlvBuilder()
        /**
         *  [SessionEnvironment] stores the pin1 value. If no pin1 value was set, it will contain
         *  default value of ‘000000’.
         *  In order to obtain card’s data, [ReadCommand] should use the correct pin 1 value.
         *  The card will not respond if wrong pin 1 has been submitted.
         */
        tlvBuilder.append(TlvTag.Pin, environment.pin1?.value)
        tlvBuilder.append(TlvTag.InteractionMode, ReadMode.ReadCard)
        tlvBuilder.append(TlvTag.TerminalPublicKey, environment.terminalKeys?.publicKey)

        return CommandApdu(Instruction.Read, tlvBuilder.serialize())
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): Card {
        return CardDeserializer.deserialize(apdu)
    }
}
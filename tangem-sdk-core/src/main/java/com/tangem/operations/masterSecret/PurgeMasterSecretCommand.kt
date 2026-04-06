package com.tangem.operations.masterSecret

import com.tangem.common.CompletionResult
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.CardSession
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.core.TangemSdkError
import com.tangem.common.deserialization.MasterSecretDeserializer
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag
import com.tangem.operations.Command
import com.tangem.operations.read.ReadMasterSecretResponse

/**
 * This command will purge the master secret on the card.
 */
class PurgeMasterSecretCommand : Command<ReadMasterSecretResponse>() {

    override fun requiresPasscode(): Boolean = true

    override fun performPreCheck(card: Card): TangemSdkError? {
        if (card.firmwareVersion < FirmwareVersion.v8) {
            return TangemSdkError.NotSupportedFirmwareVersion()
        }

        return null
    }

    override fun run(session: CardSession, callback: CompletionCallback<ReadMasterSecretResponse>) {
        super.run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    session.environment.card = session.environment.card?.copy(
                        masterSecret = result.data.masterSecret,
                    )
                    callback(CompletionResult.Success(result.data))
                }
                is CompletionResult.Failure -> callback(result)
            }
        }
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = createTlvBuilder(environment.legacyMode)

        tlvBuilder.append(TlvTag.InteractionMode, ManageMasterSecretMode.Purge)

        return CommandApdu(Instruction.ManageMasterSecret, tlvBuilder.serialize())
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): ReadMasterSecretResponse {
        val tlvData = apdu.getTlvData() ?: throw TangemSdkError.DeserializeApduFailed()

        val decoder = TlvDecoder(tlvData)
        val masterSecret = MasterSecretDeserializer.deserializeMasterSecret(decoder)

        return ReadMasterSecretResponse(masterSecret)
    }
}
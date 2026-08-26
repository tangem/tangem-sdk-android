package com.tangem.operations.read

import com.squareup.moshi.JsonClass
import com.tangem.common.CompletionResult
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.card.MasterSecret
import com.tangem.common.core.*
import com.tangem.common.deserialization.MasterSecretDeserializer
import com.tangem.common.tlv.TlvTag
import com.tangem.operations.Command
import com.tangem.operations.CommandResponse
import com.tangem.operations.PreflightReadMode

@JsonClass(generateAdapter = true)
data class ReadMasterSecretResponse(
    val masterSecret: MasterSecret?,
) : CommandResponse

/**
 * This command receives from the Tangem Card
 */
class ReadMasterSecretCommand : Command<ReadMasterSecretResponse>() {

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.ReadCardOnly

    override fun performPreCheck(card: Card): TangemError? {
        if (card.firmwareVersion < FirmwareVersion.v8) {
            return TangemSdkError.NotSupportedFirmwareVersion()
        }
        return null
    }

    override fun run(session: CardSession, callback: CompletionCallback<ReadMasterSecretResponse>) {
        super.run(session) {
            when (it) {
                is CompletionResult.Success -> {
                    callback(it)
                }
                is CompletionResult.Failure -> callback(CompletionResult.Failure(it.error))
            }
        }
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = createTlvBuilder(environment.legacyMode)
        tlvBuilder.append(TlvTag.InteractionMode, ReadMode.MasterSecret)

        return CommandApdu(Instruction.Read, tlvBuilder.serialize())
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): ReadMasterSecretResponse {
        val decoder = createTlvDecoder(environment, apdu)
        val masterSecret = MasterSecretDeserializer.deserializeMasterSecret(decoder)
        return ReadMasterSecretResponse(masterSecret)
    }
}
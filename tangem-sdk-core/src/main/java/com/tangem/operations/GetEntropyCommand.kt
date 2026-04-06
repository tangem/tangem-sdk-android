package com.tangem.operations

import com.squareup.moshi.JsonClass
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.CardSessionEncryption
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.core.TangemError
import com.tangem.common.core.TangemSdkError
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvTag

@JsonClass(generateAdapter = true)
class GetEntropyResponse(
    val cardId: String,
    val data: ByteArray,
) : CommandResponse

class GetEntropyCommand : Command<GetEntropyResponse>() {

    override var cardSessionEncryption = CardSessionEncryption.PUBLIC_SECURE_CHANNEL

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.ReadCardOnly

    override fun performPreCheck(card: Card): TangemError? {
        if (card.firmwareVersion < FirmwareVersion.KeysImportAvailable) {
            return TangemSdkError.NotSupportedFirmwareVersion()
        }
        return null
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val card = environment.card ?: throw TangemSdkError.MissingPreflightRead()

        val tlvBuilder = createTlvBuilder(environment.legacyMode)
        if (shouldAddPin(environment.accessCode, card.firmwareVersion)) {
            tlvBuilder.append(TlvTag.Pin, environment.accessCode.value)
        }
        if (card.firmwareVersion < FirmwareVersion.v8) {
            tlvBuilder.append(TlvTag.CardId, environment.card?.cardId)
        }

        return CommandApdu(Instruction.GetEntropy, tlvBuilder.serialize())
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): GetEntropyResponse {
        val decoder = createTlvDecoder(environment, apdu)
        return GetEntropyResponse(cardId = decoder.decode(TlvTag.CardId), data = decoder.decode(TlvTag.IssuerData))
    }
}
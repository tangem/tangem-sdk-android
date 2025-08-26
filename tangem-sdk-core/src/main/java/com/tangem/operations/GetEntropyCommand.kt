package com.tangem.operations

import com.squareup.moshi.JsonClass
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.core.TangemError
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.guard
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag
import com.tangem.crypto.hdWallet.DerivationPath

@JsonClass(generateAdapter = true)
class GetEntropyResponse(
    val cardId: String,
    val data: ByteArray,
) : CommandResponse

enum class GetEntropyMode(val rawValue: Int) {
    Random(rawValue = 0x00),
    Deterministic(rawValue = 0x01),
    ;

    companion object {
        private val values = GetEntropyMode.values()
        fun byRawValue(rawValue: Int): GetEntropyMode? = values.find { it.rawValue == rawValue }
    }
}

class GetEntropyCommand(private val mode: GetEntropyMode, private val derivationPath: DerivationPath? = null) :
    Command<GetEntropyResponse>() {

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.ReadCardOnly

    override fun performPreCheck(card: Card): TangemError? {
        if (card.firmwareVersion < FirmwareVersion.KeysImportAvailable) { //TODO
            return TangemSdkError.WalletNotFound() //TODO
        }
        if( card.firmwareVersion<FirmwareVersion.MasterSecretAvailable && mode!=GetEntropyMode.Random ) {
            return TangemSdkError.WalletNotFound() //TODO
        }
        return null
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val card = environment.card.guard {
            throw TangemSdkError.MissingPreflightRead()
        }
        val tlvBuilder = TlvBuilder()
        if( card.firmwareVersion<FirmwareVersion.MasterSecretAvailable ) {
            tlvBuilder.append(tag = TlvTag.Pin, value = environment.accessCode.value)
            tlvBuilder.append(tag = TlvTag.CardId, value = environment.card?.cardId)
        }else{
            tlvBuilder.append(tag = TlvTag.InteractionMode, value = mode)
            tlvBuilder.append(tag = TlvTag.WalletHDPath, value = derivationPath)
        }

        return CommandApdu(Instruction.GetEntropy, tlvBuilder.serialize())
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): GetEntropyResponse {
        val tlvData = apdu.getTlvData() ?: throw TangemSdkError.DeserializeApduFailed()

        val decoder = TlvDecoder(tlvData)
        return GetEntropyResponse(cardId = decoder.decode(TlvTag.CardId), data = decoder.decode(TlvTag.IssuerData))
    }
}
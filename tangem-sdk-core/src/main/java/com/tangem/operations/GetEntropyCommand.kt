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
import com.tangem.common.tlv.InteractionMode
import com.tangem.common.tlv.TlvTag
import com.tangem.crypto.hdWallet.DerivationPath

@JsonClass(generateAdapter = true)
class GetEntropyResponse(
    val cardId: String,
    val data: ByteArray,
) : CommandResponse

sealed class GetEntropyMode(override val rawValue: Byte) : InteractionMode {
    object Random : GetEntropyMode(0x00)
    class Deterministic(val derivationPath: DerivationPath) : GetEntropyMode(0x01)
}

class GetEntropyCommand(
    private val mode: GetEntropyMode = GetEntropyMode.Random,
) : Command<GetEntropyResponse>() {

    override var cardSessionEncryption = CardSessionEncryption.PUBLIC_SECURE_CHANNEL

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.ReadCardOnly

    override fun performPreCheck(card: Card): TangemError? {
        if (card.firmwareVersion < FirmwareVersion.KeysImportAvailable) {
            return TangemSdkError.NotSupportedFirmwareVersion()
        }

        when (mode) {
            is GetEntropyMode.Random -> Unit
            is GetEntropyMode.Deterministic -> {
                if (card.firmwareVersion < FirmwareVersion.v8) {
                    return TangemSdkError.NotSupportedFirmwareVersion()
                }

                if (card.settings.isBackupRequired && card.backupStatus?.isActive != true) {
                    return TangemSdkError.NoActiveBackup()
                }

                if (mode.derivationPath.nodes.any { !it.isHardened }) {
                    return TangemSdkError.NonHardenedDerivationNotSupported()
                }
            }
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
            tlvBuilder.append(TlvTag.CardId, card.cardId)
        } else {
            tlvBuilder.append(TlvTag.InteractionMode, mode)
            if (mode is GetEntropyMode.Deterministic) {
                tlvBuilder.append(TlvTag.WalletHDPath, mode.derivationPath)
            }
        }

        return CommandApdu(Instruction.GetEntropy, tlvBuilder.serialize())
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): GetEntropyResponse {
        val decoder = createTlvDecoder(environment, apdu)
        return GetEntropyResponse(cardId = decoder.decode(TlvTag.CardId), data = decoder.decode(TlvTag.Data))
    }
}
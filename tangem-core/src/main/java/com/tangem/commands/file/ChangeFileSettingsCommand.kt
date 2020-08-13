package com.tangem.commands.file

import com.tangem.SessionEnvironment
import com.tangem.TangemError
import com.tangem.TangemSdkError
import com.tangem.commands.Card
import com.tangem.commands.CardStatus
import com.tangem.commands.Command
import com.tangem.commands.SimpleResponse
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag

enum class FileSettings(val rawValue: Int) {
    Public(0x0001),
    Private(0x0000),
    ;

    companion object {
        private val values = FileSettings.values()
        fun byRawValue(rawValue: Int): FileSettings? = values.find { it.rawValue == rawValue }
    }
}

typealias ChangeFileSettingsResponse = SimpleResponse

/**
 * Card Command is in development, subject to future changes
 */
class ChangeFileSettingsCommand(
        private val settings: FileSettings,
        private val fileIndex: Int
) : Command<ChangeFileSettingsResponse>() {

    override val requiresPin2 = true

    override fun performPreCheck(card: Card): TangemSdkError? {
        if (card.status == CardStatus.NotPersonalized) {
            return TangemSdkError.NotPersonalized()
        }
        if (card.isActivated) {
            return TangemSdkError.NotActivated()
        }
        return null
    }

    override fun mapError(card: Card?, error: TangemError): TangemError {
        if (error is TangemSdkError.InvalidParams) {
            return TangemSdkError.Pin2OrCvcRequired()
        }
        return error
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = TlvBuilder()

        tlvBuilder.append(TlvTag.CardId, environment.card?.cardId)
        tlvBuilder.append(TlvTag.Pin, environment.pin1?.value)
        tlvBuilder.append(TlvTag.Pin2, environment.pin2?.value)
        tlvBuilder.append(TlvTag.WriteFileMode, FileDataMode.ChangeFileSettings)
        tlvBuilder.append(TlvTag.FileIndex, fileIndex)
        tlvBuilder.append(TlvTag.FileSettings, settings)

        return CommandApdu(Instruction.WriteFileData, tlvBuilder.serialize())
    }

    override fun deserialize(
            environment: SessionEnvironment,
            apdu: ResponseApdu
    ): ChangeFileSettingsResponse {
        val tlvData = apdu.getTlvData() ?: throw TangemSdkError.DeserializeApduFailed()

        val decoder = TlvDecoder(tlvData)
        return DeleteFileResponse(cardId = decoder.decode(TlvTag.CardId))
    }
}

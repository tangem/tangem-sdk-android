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
import com.tangem.common.extensions.getFirmwareNumber
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

data class FileSettingsChange(
        val fileIndex: Int,
        val settings: FileSettings
)

/**
 * This command allows to change settings of a file written to the card with [WriteFileDataCommand].
 * Passcode (PIN2) is required for this operation.
 * [FileSettings] change access level to a file - it can be [FileSettings.Private],
 * accessible only with PIN2, or [FileSettings.Public], accessible without PIN2
 *
 * @property data contains index of a file that is to be changed and desired settings.
 */
class ChangeFileSettingsCommand(
        private val data: FileSettingsChange
) : Command<ChangeFileSettingsResponse>() {

    override val requiresPin2 = true

    override fun performPreCheck(card: Card): TangemSdkError? {
        if (card.status == CardStatus.NotPersonalized) {
            return TangemSdkError.NotPersonalized()
        }
        if (card.isActivated) {
            return TangemSdkError.NotActivated()
        }
        if (card.getFirmwareNumber() ?: 0.0 < 3.29) {
            return TangemSdkError.FirmwareNotSupported()
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
        tlvBuilder.append(TlvTag.FileIndex, data.fileIndex)
        tlvBuilder.append(TlvTag.FileSettings, data.settings)

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

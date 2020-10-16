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


typealias DeleteFileResponse = SimpleResponse

/**
 * This command allows to delete data written to the card with [WriteFileDataCommand].
 * Passcode (PIN2) is required to delete the files.
 *
 * @property fileIndex index of a file to be deleted.
 */
class DeleteFileCommand(private val fileIndex: Int) : Command<DeleteFileResponse>() {

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
        tlvBuilder.append(TlvTag.WriteFileMode, FileDataMode.DeleteFile)
        tlvBuilder.append(TlvTag.FileIndex, fileIndex)

        return CommandApdu(Instruction.WriteFileData, tlvBuilder.serialize())
    }

    override fun deserialize(
            environment: SessionEnvironment,
            apdu: ResponseApdu
    ): DeleteFileResponse {
        val tlvData = apdu.getTlvData() ?: throw TangemSdkError.DeserializeApduFailed()

        val decoder = TlvDecoder(tlvData)
        return DeleteFileResponse(cardId = decoder.decode(TlvTag.CardId))
    }
}
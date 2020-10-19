package com.tangem.commands.file

import com.tangem.SessionEnvironment
import com.tangem.TangemError
import com.tangem.TangemSdkError
import com.tangem.commands.Card
import com.tangem.commands.CardStatus
import com.tangem.commands.Command
import com.tangem.commands.CommandResponse
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag

class ReadFileHashResponse(
        val cardId: String,
        val fileHash: ByteArray,
        val fileIndex: Int?
) : CommandResponse

/**
 * This command allows to read a SHA256 hash of a data written to the card
 * with [WriteFileDataCommand] using PIN2.
 * This may be used to check integrity of a file.
 *
 * @property fileIndex index of a file
 * @property readPrivateFiles if set to true, then the command will get hashes of private files.
 */
class ReadFileHashCommand(
        private val fileIndex: Int = 0,
        private val readPrivateFiles: Boolean = false
) : Command<ReadFileHashResponse>() {

    override val requiresPin2 = readPrivateFiles

    override fun performPreCheck(card: Card): TangemSdkError? {
        if (card.status == CardStatus.NotPersonalized) {
            return TangemSdkError.NotPersonalized()
        }
        return null
    }

    override fun mapError(card: Card?, error: TangemError): TangemError {
        if (error is TangemSdkError.InvalidParams && requiresPin2) {
            return TangemSdkError.Pin2OrCvcRequired()
        }
        return error
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = TlvBuilder()
        tlvBuilder.append(TlvTag.CardId, environment.card?.cardId)
        tlvBuilder.append(TlvTag.Pin, environment.pin1?.value)
        if (readPrivateFiles) tlvBuilder.append(TlvTag.Pin2, environment.pin2?.value)
        tlvBuilder.append(TlvTag.FileIndex, fileIndex)
        tlvBuilder.append(TlvTag.WriteFileMode, FileDataMode.ReadFileHash)
        return CommandApdu(Instruction.ReadFileData, tlvBuilder.serialize())
    }

    override fun deserialize(
            environment: SessionEnvironment,
            apdu: ResponseApdu
    ): ReadFileHashResponse {
        val tlvData = apdu.getTlvData() ?: throw TangemSdkError.DeserializeApduFailed()

        val decoder = TlvDecoder(tlvData)
        return ReadFileHashResponse(
                cardId = decoder.decode(TlvTag.CardId),
                fileHash = decoder.decode(TlvTag.CodeHash),
                fileIndex = decoder.decodeOptional(TlvTag.FileIndex),
        )
    }
}
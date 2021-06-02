package com.tangem.commands.file

import com.squareup.moshi.JsonClass
import com.tangem.FirmwareConstraints
import com.tangem.SessionEnvironment
import com.tangem.TangemSdkError
import com.tangem.commands.Command
import com.tangem.commands.CommandResponse
import com.tangem.commands.common.card.Card
import com.tangem.commands.common.card.CardStatus
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag

@JsonClass(generateAdapter = true)
class ReadFileChecksumResponse(
    val cardId: String,
    val checksum: ByteArray,
    val fileIndex: Int?
) : CommandResponse

/**
 * This command allows to read a SHA256 hash of a data written to the card
 * with [WriteFileCommand] using PIN2.
 * This may be used to check integrity of a file.
 *
 * @property fileIndex index of a file
 * @property readPrivateFiles if set to true, then the command will get hashes of private files.
 */
class ReadFileChecksumCommand(
        private val fileIndex: Int = 0,
        private val readPrivateFiles: Boolean = false
) : Command<ReadFileChecksumResponse>() {

    override fun requiresPin2(): Boolean = readPrivateFiles

    override fun performPreCheck(card: Card): TangemSdkError? {
        if (card.status == CardStatus.NotPersonalized) {
            return TangemSdkError.NotPersonalized()
        }
        if (card.firmwareVersion < FirmwareConstraints.AvailabilityVersions.files) {
            return TangemSdkError.FirmwareNotSupported()
        }
        return null
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
    ): ReadFileChecksumResponse {
        val tlvData = apdu.getTlvData() ?: throw TangemSdkError.DeserializeApduFailed()

        val decoder = TlvDecoder(tlvData)
        return ReadFileChecksumResponse(
                cardId = decoder.decode(TlvTag.CardId),
                checksum = decoder.decode(TlvTag.CodeHash),
                fileIndex = decoder.decodeOptional(TlvTag.FileIndex),
        )
    }
}
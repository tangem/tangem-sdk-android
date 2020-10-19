package com.tangem.commands.file

import com.tangem.CardSession
import com.tangem.SessionEnvironment
import com.tangem.TangemError
import com.tangem.TangemSdkError
import com.tangem.commands.Card
import com.tangem.commands.CardStatus
import com.tangem.commands.Command
import com.tangem.commands.CommandResponse
import com.tangem.common.CompletionResult
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.extensions.getFirmwareNumber
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag
import java.io.ByteArrayOutputStream

class ReadFileDataResponse(
        val cardId: String,
        val size: Int?,
        val fileData: ByteArray,
        val fileIndex: Int,
        val fileSettings: FileSettings?,
        val fileDataSignature: ByteArray?,
        val fileDataCounter: Int?
) : CommandResponse

/**
 * This command allows to read data written to the card with [WriteFileDataCommand].
 * If the files are private, then Passcode (PIN2) is required to read the files.
 *
 * @property fileIndex index of a file
 * @property readPrivateFiles if set to true, then the command will read private files,
 * for which it requires PIN2. Otherwise only public files can be read.
 */
class ReadFileDataCommand(
        private val fileIndex: Int = 0,
        private val readPrivateFiles: Boolean = false
) : Command<ReadFileDataResponse>() {

    override val requiresPin2 = readPrivateFiles

    private val fileData = ByteArrayOutputStream()
    private var offset: Int = 0
    private var dataSize: Int = 0
    private var fileSettings: FileSettings? = null

    override fun performPreCheck(card: Card): TangemSdkError? {
        if (card.status == CardStatus.NotPersonalized) {
            return TangemSdkError.NotPersonalized()
        }
        if (card.getFirmwareNumber() ?: 0.0 < 3.29) {
            return TangemSdkError.FirmwareNotSupported()
        }
        return null
    }

    override fun mapError(card: Card?, error: TangemError): TangemError {
        if (error is TangemSdkError.InvalidParams && requiresPin2) {
            return TangemSdkError.Pin2OrCvcRequired()
        }
        return error
    }

    override fun run(
            session: CardSession,
            callback: (result: CompletionResult<ReadFileDataResponse>) -> Unit
    ) {
        readFileData(session, callback)
    }

    private fun readFileData(
            session: CardSession, callback: (result: CompletionResult<ReadFileDataResponse>) -> Unit
    ) {

        if (dataSize != 0) {
            session.viewDelegate.onDelay(
                    dataSize, offset, WriteFileDataCommand.SINGLE_WRITE_SIZE
            )
        }

        transceive(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    if (result.data.size != null) {
                        if (result.data.size == 0) {
                            callback(CompletionResult.Success(result.data))
                            return@transceive
                        }
                        dataSize = result.data.size
                        fileSettings = result.data.fileSettings
                    }
                    fileData.write(result.data.fileData)
                    if (result.data.fileDataCounter == null) {
                        offset = fileData.size()
                        readFileData(session, callback)
                    } else {
                        completeTask(result.data, callback)
                    }
                }
                is CompletionResult.Failure -> {
                    callback(CompletionResult.Failure(result.error))
                }
            }
        }
    }

    private fun completeTask(
            data: ReadFileDataResponse,
            callback: (result: CompletionResult<ReadFileDataResponse>) -> Unit
    ) {
        val finalResult = ReadFileDataResponse(
                data.cardId,
                dataSize,
                fileData.toByteArray(),
                data.fileIndex,
                fileSettings,
                data.fileDataSignature,
                data.fileDataCounter
        )
        callback(CompletionResult.Success(finalResult))
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = TlvBuilder()
        tlvBuilder.append(TlvTag.CardId, environment.card?.cardId)
        tlvBuilder.append(TlvTag.Pin, environment.pin1?.value)
        if (readPrivateFiles) tlvBuilder.append(TlvTag.Pin2, environment.pin2?.value)
        tlvBuilder.append(TlvTag.FileIndex, fileIndex)
        tlvBuilder.append(TlvTag.Offset, offset)
        return CommandApdu(Instruction.ReadFileData, tlvBuilder.serialize())
    }

    override fun deserialize(
            environment: SessionEnvironment,
            apdu: ResponseApdu
    ): ReadFileDataResponse {
        val tlvData = apdu.getTlvData() ?: throw TangemSdkError.DeserializeApduFailed()

        val decoder = TlvDecoder(tlvData)
        return ReadFileDataResponse(
                cardId = decoder.decode(TlvTag.CardId),
                size = decoder.decodeOptional(TlvTag.Size),
                fileData = decoder.decodeOptional(TlvTag.IssuerData) ?: byteArrayOf(),
                fileIndex = decoder.decodeOptional(TlvTag.FileIndex) ?: 0,
                fileSettings = decoder.decodeOptional(TlvTag.FileSettings),
                fileDataSignature = decoder.decodeOptional(TlvTag.IssuerDataSignature),
                fileDataCounter = decoder.decodeOptional(TlvTag.IssuerDataCounter)
        )
    }
}
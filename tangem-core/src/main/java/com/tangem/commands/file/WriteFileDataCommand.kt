package com.tangem.commands.file

import com.tangem.CardSession
import com.tangem.SessionEnvironment
import com.tangem.TangemError
import com.tangem.TangemSdkError
import com.tangem.commands.*
import com.tangem.commands.common.DefaultIssuerDataVerifier
import com.tangem.commands.common.IssuerDataToVerify
import com.tangem.commands.common.IssuerDataVerifier
import com.tangem.commands.file.WriteFileDataCommand.Companion.MAX_SIZE
import com.tangem.common.CompletionResult
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.extensions.calculateSha256
import com.tangem.common.extensions.getFirmwareNumber
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag

class WriteFileDataResponse(
        val cardId: String,
        val fileIndex: Int? = null
) : CommandResponse

class FileDataSignature(
        val startingSignature: ByteArray,
        val finalizingSignature: ByteArray,
)

sealed class FileData(val data: ByteArray) {
    class DataProtectedBySignature(
            data: ByteArray,
            val counter: Int,
            val signature: FileDataSignature,
            val issuerPublicKey: ByteArray? = null
    ) : FileData(data)

    class DataProtectedByPasscode(data: ByteArray) : FileData(data)
}

/**
 * This command allows to write data up to [MAX_SIZE] to a card.
 * There are two secure ways to write files.
 * 1) Data can be signed by Issuer (the one specified on card during personalization) -
 * [FileData.DataProtectedBySignature].
 * 2) Data can be protected by Passcode (PIN2). [FileData.DataProtectedByPasscode] In this case,
 * Passcode (PIN2) is required for the command.
 *
 * @property fileData data to be written.
 */
class WriteFileDataCommand(
        private val fileData: FileData,
        verifier: IssuerDataVerifier = DefaultIssuerDataVerifier()
) : Command<WriteFileDataResponse>(), IssuerDataVerifier by verifier {

    override val requiresPin2: Boolean = fileData is FileData.DataProtectedByPasscode

    private var mode: FileDataMode = FileDataMode.InitiateWritingFile
    private var offset: Int = 0
    private var fileIndex: Int = 0

    override fun run(
            session: CardSession,
            callback: (result: CompletionResult<WriteFileDataResponse>) -> Unit
    ) {
        writeFileData(session, callback)
    }

    override fun performPreCheck(card: Card): TangemSdkError? {
        val firmwareVersion = card.getFirmwareNumber() ?: 0.0
        when (fileData) {
            is FileData.DataProtectedByPasscode ->
                if (firmwareVersion < 3.37) return TangemSdkError.FirmwareNotSupported()
            is FileData.DataProtectedBySignature ->
                if (firmwareVersion < 3.29) return TangemSdkError.FirmwareNotSupported()
        }
        if (fileData is FileData.DataProtectedBySignature) {
            val publicKey = fileData.issuerPublicKey ?: card.issuerPublicKey
            ?: return TangemSdkError.MissingIssuerPubicKey()
            if (!isCounterValid(fileData.counter, card)) {
                return TangemSdkError.MissingCounter()
            }
            if (!verifySignatures(fileData, publicKey, card.cardId)) {
                return TangemSdkError.VerificationFailed()
            }
        }
        if (fileData.data.size > MAX_SIZE) {
            return TangemSdkError.DataSizeTooLarge()
        }
        if (card.status == CardStatus.NotPersonalized) {
            return TangemSdkError.NotPersonalized()
        }
        if (card.isActivated) {
            return TangemSdkError.NotActivated()
        }

        return null
    }

    override fun mapError(card: Card?, error: TangemError): TangemError {
        if (error is TangemSdkError.InvalidParams && isCounterRequired(card)) {
            return TangemSdkError.DataCannotBeWritten()
        }
        if (error is TangemSdkError.InvalidState &&
                card?.settingsMask?.contains(Settings.ProtectIssuerDataAgainstReplay) == true) {
            return TangemSdkError.OverwritingDataIsProhibited()
        }
        if (error is TangemSdkError.InvalidParams) {
            return TangemSdkError.Pin2OrCvcRequired()
        }
        return error
    }

    private fun isCounterValid(issuerDataCounter: Int?, card: Card): Boolean =
            if (isCounterRequired(card)) issuerDataCounter != null else true

    private fun isCounterRequired(card: Card?): Boolean {
        if (fileData is FileData.DataProtectedByPasscode) return false
        return card?.settingsMask?.contains(Settings.ProtectIssuerDataAgainstReplay) == true
    }

    private fun verifySignatures(
            fileData: FileData.DataProtectedBySignature, publicKey: ByteArray, cardId: String
    ): Boolean {
        val firstData = IssuerDataToVerify(cardId, null, fileData.counter, fileData.data.size)
        val secondData = IssuerDataToVerify(cardId, fileData.data, fileData.counter)

        return verify(publicKey, fileData.signature.startingSignature, firstData) &&
                verify(publicKey, fileData.signature.finalizingSignature, secondData)
    }

    private fun writeFileData(
            session: CardSession,
            callback: (result: CompletionResult<WriteFileDataResponse>) -> Unit
    ) {

        if (mode == FileDataMode.WriteFile) {
            session.viewDelegate.onDelay(
                    fileData.data.size,
                    offset,
                    SINGLE_WRITE_SIZE
            )
        }
        transceive(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    when (mode) {
                        FileDataMode.InitiateWritingFile -> {
                            result.data.fileIndex?.let { fileIndex = it }
                            mode = FileDataMode.WriteFile
                            writeFileData(session, callback)
                            return@transceive
                        }
                        FileDataMode.WriteFile -> {
                            offset += WriteIssuerExtraDataCommand.SINGLE_WRITE_SIZE
                            if (offset >= fileData.data.size) {
                                mode = FileDataMode.ConfirmWritingFile
                            }
                            writeFileData(session, callback)
                            return@transceive
                        }
                        FileDataMode.ConfirmWritingFile -> {
                            callback(CompletionResult.Success(
                                    WriteFileDataResponse(result.data.cardId, fileIndex)
                            ))
                        }
                    }
                }
                is CompletionResult.Failure -> {
                    if (session.environment.handleErrors) {
                        val error = mapError(session.environment.card, result.error)
                        callback(CompletionResult.Failure(error))
                    }
                    callback(CompletionResult.Failure(result.error))
                }
            }
        }


    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = TlvBuilder()

        tlvBuilder.append(TlvTag.CardId, environment.card?.cardId)
        tlvBuilder.append(TlvTag.Pin, environment.pin1?.value)
        tlvBuilder.append(TlvTag.WriteFileMode, mode)

        when (mode) {
            FileDataMode.InitiateWritingFile -> {
                tlvBuilder.append(TlvTag.Size, fileData.data.size)
                if (fileData is FileData.DataProtectedBySignature) {
                    tlvBuilder.append(TlvTag.IssuerDataSignature, fileData.signature.startingSignature)
                    tlvBuilder.append(TlvTag.IssuerDataCounter, fileData.counter)
                } else {
                    tlvBuilder.append(TlvTag.Pin2, environment.pin2?.value)
                }

            }
            FileDataMode.WriteFile -> {
                tlvBuilder.append(TlvTag.IssuerData, getDataToWrite())
                tlvBuilder.append(TlvTag.Offset, offset)
                tlvBuilder.append(TlvTag.FileIndex, fileIndex)
            }
            FileDataMode.ConfirmWritingFile -> {
                if (fileData is FileData.DataProtectedBySignature) {
                    tlvBuilder.append(TlvTag.IssuerDataSignature, fileData.signature.finalizingSignature)
                } else {
                    tlvBuilder.append(TlvTag.CodeHash, fileData.data.calculateSha256())
                    tlvBuilder.append(TlvTag.Pin2, environment.pin2?.value)
                }
                tlvBuilder.append(TlvTag.FileIndex, fileIndex)
            }
        }
        return CommandApdu(Instruction.WriteFileData, tlvBuilder.serialize())
    }

    private fun getDataToWrite(): ByteArray =
            fileData.data.copyOfRange(offset, offset + calculatePartSize())

    private fun calculatePartSize(): Int {
        val bytesLeft = fileData.data.size - offset
        return if (bytesLeft < SINGLE_WRITE_SIZE) bytesLeft else SINGLE_WRITE_SIZE
    }

    override fun deserialize(
            environment: SessionEnvironment,
            apdu: ResponseApdu
    ): WriteFileDataResponse {
        val tlvData = apdu.getTlvData() ?: throw TangemSdkError.DeserializeApduFailed()
        val decoder = TlvDecoder(tlvData)
        return WriteFileDataResponse(
                cardId = decoder.decode(TlvTag.CardId),
                fileIndex = decoder.decodeOptional(TlvTag.FileIndex)
        )
    }

    companion object {
        const val SINGLE_WRITE_SIZE = 1524
        const val MAX_SIZE = 48 * 1024
    }
}
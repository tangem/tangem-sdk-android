package com.tangem.commands.file

import com.tangem.CardSession
import com.tangem.SessionEnvironment
import com.tangem.TangemError
import com.tangem.TangemSdkError
import com.tangem.commands.*
import com.tangem.commands.common.DefaultIssuerDataVerifier
import com.tangem.commands.common.IssuerDataToVerify
import com.tangem.commands.common.IssuerDataVerifier
import com.tangem.common.CompletionResult
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag

class WriteFileDataResponse(
        val cardId: String,
        val fileIndex: Int? = null
) : CommandResponse

/**
 * Card Command is in development, subject to future changes
 */
class WriteFileDataCommand(
        private val data: ByteArray,
        private val startingSignature: ByteArray,
        private val finalizingSignature: ByteArray,
        private val dataCounter: Int? = null,
        private val issuerPublicKey: ByteArray? = null,
        verifier: IssuerDataVerifier = DefaultIssuerDataVerifier()
) : Command<WriteFileDataResponse>(), IssuerDataVerifier by verifier {

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
        val publicKey = issuerPublicKey ?: card.issuerPublicKey
        ?: return TangemSdkError.MissingIssuerPubicKey()

        if (card.status == CardStatus.NotPersonalized) {
            return TangemSdkError.NotPersonalized()
        }
        if (card.isActivated) {
            return TangemSdkError.NotActivated()
        }
        if (data.size > MAX_SIZE) {
            return TangemSdkError.DataSizeTooLarge()
        }
        if (!isCounterValid(dataCounter, card)) {
            return TangemSdkError.MissingCounter()
        }
        if (!verifySignatures(publicKey, card.cardId)) {
            return TangemSdkError.VerificationFailed()
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
        return error
    }

    private fun isCounterValid(issuerDataCounter: Int?, card: Card): Boolean =
            if (isCounterRequired(card)) issuerDataCounter != null else true

    private fun isCounterRequired(card: Card?): Boolean =
            card?.settingsMask?.contains(Settings.ProtectIssuerDataAgainstReplay) == true

    private fun verifySignatures(publicKey: ByteArray, cardId: String): Boolean {

        val firstData = IssuerDataToVerify(cardId, null, dataCounter, data.size)
        val secondData = IssuerDataToVerify(cardId, data, dataCounter)

        return verify(publicKey, startingSignature, firstData) &&
                verify(publicKey, finalizingSignature, secondData)
    }

    private fun writeFileData(
            session: CardSession,
            callback: (result: CompletionResult<WriteFileDataResponse>) -> Unit
    ) {

        if (mode == FileDataMode.WriteFile) {
            session.viewDelegate.onDelay(
                    data.size,
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
                            if (offset >= data.size) {
                                mode = FileDataMode.ConfirmWritingFile
                            }
                            writeFileData(session, callback)
                            return@transceive
                        }
                        FileDataMode.ConfirmWritingFile -> {
                            callback(CompletionResult.Success(result.data))
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
                tlvBuilder.append(TlvTag.Size, data.size)
                tlvBuilder.append(TlvTag.IssuerDataSignature, startingSignature)
                tlvBuilder.append(TlvTag.IssuerDataCounter, dataCounter)
            }
            FileDataMode.WriteFile -> {
                tlvBuilder.append(TlvTag.IssuerData, getDataToWrite())
                tlvBuilder.append(TlvTag.Offset, offset)
                tlvBuilder.append(TlvTag.FileIndex, fileIndex)
            }
            FileDataMode.ConfirmWritingFile -> {
                tlvBuilder.append(TlvTag.IssuerDataSignature, finalizingSignature)
                tlvBuilder.append(TlvTag.FileIndex, fileIndex)
            }
        }
        return CommandApdu(Instruction.WriteFileData, tlvBuilder.serialize())
    }

    private fun getDataToWrite(): ByteArray =
            data.copyOfRange(offset, offset + calculatePartSize())

    private fun calculatePartSize(): Int {
        val bytesLeft = data.size - offset
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
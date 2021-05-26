package com.tangem.commands.file

import com.squareup.moshi.JsonClass
import com.tangem.*
import com.tangem.commands.Command
import com.tangem.commands.CommandResponse
import com.tangem.commands.common.DefaultIssuerDataVerifier
import com.tangem.commands.common.IssuerDataToVerify
import com.tangem.commands.common.IssuerDataVerifier
import com.tangem.commands.common.card.Card
import com.tangem.commands.common.card.CardStatus
import com.tangem.commands.common.card.FirmwareVersion
import com.tangem.commands.common.card.masks.Settings
import com.tangem.commands.file.WriteFileCommand.Companion.MAX_SIZE
import com.tangem.common.CompletionResult
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.extensions.calculateSha256
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag

@JsonClass(generateAdapter = true)
class WriteFileResponse(
    val cardId: String,
    val fileIndex: Int? = null
) : CommandResponse

class FileDataSignature(
    val startingSignature: ByteArray,
    val finalizingSignature: ByteArray,
)

interface FirmwareRestrictible {
    val minFirmwareVersion: FirmwareVersion
    val maxFirmwareVersion: FirmwareVersion
}

sealed class DataToWrite(val data: ByteArray) : FirmwareRestrictible {
    class DataProtectedBySignature(
        data: ByteArray,
        val counter: Int,
        val signature: FileDataSignature,
        val issuerPublicKey: ByteArray? = null
    ) : DataToWrite(data) {
        override val minFirmwareVersion: FirmwareVersion = FirmwareVersion(3, 29)
        override val maxFirmwareVersion: FirmwareVersion = FirmwareVersion.max
    }

    class DataProtectedByPasscode(data: ByteArray) : DataToWrite(data) {
        override val minFirmwareVersion: FirmwareVersion = FirmwareVersion(3, 34)
        override val maxFirmwareVersion: FirmwareVersion = FirmwareVersion.max
    }
}

/**
 * This command allows to write data up to [MAX_SIZE] to a card.
 * There are two secure ways to write files.
 * 1) Data can be signed by Issuer (the one specified on card during personalization) -
 * [DataToWrite.DataProtectedBySignature].
 * 2) Data can be protected by Passcode (PIN2). [DataToWrite.DataProtectedByPasscode] In this case,
 * Passcode (PIN2) is required for the command.
 *
 * @property dataToWrite data to be written.
 */
class WriteFileCommand(
    private val dataToWrite: DataToWrite,
    verifier: IssuerDataVerifier = DefaultIssuerDataVerifier()
) : Command<WriteFileResponse>(), IssuerDataVerifier by verifier {

    private var mode: FileDataMode = FileDataMode.InitiateWritingFile
    private var offset: Int = 0
    private var fileIndex: Int = 0

    override fun requiresPin2(): Boolean = dataToWrite is DataToWrite.DataProtectedByPasscode

    override fun run(
        session: CardSession,
        callback: (result: CompletionResult<WriteFileResponse>) -> Unit
    ) {
        writeFileData(session, callback)
    }

    override fun performPreCheck(card: Card): TangemSdkError? {
        if (card.firmwareVersion < FirmwareConstraints.AvailabilityVersions.files ||
            card.firmwareVersion < dataToWrite.minFirmwareVersion)
            return TangemSdkError.FirmwareNotSupported()

        if (dataToWrite is DataToWrite.DataProtectedBySignature) {
            val publicKey = dataToWrite.issuerPublicKey ?: card.issuerPublicKey
            ?: return TangemSdkError.MissingIssuerPubicKey()
            if (!isCounterValid(dataToWrite.counter, card)) {
                return TangemSdkError.MissingCounter()
            }
            if (!verifySignatures(dataToWrite, publicKey, card.cardId)) {
                return TangemSdkError.VerificationFailed()
            }
        }
        if (dataToWrite.data.size > MAX_SIZE) {
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
        return error
    }

    private fun isCounterValid(issuerDataCounter: Int?, card: Card): Boolean =
        if (isCounterRequired(card)) issuerDataCounter != null else true

    private fun isCounterRequired(card: Card?): Boolean {
        if (dataToWrite is DataToWrite.DataProtectedByPasscode) return false
        return card?.settingsMask?.contains(Settings.ProtectIssuerDataAgainstReplay) == true
    }

    private fun verifySignatures(
        dataToWrite: DataToWrite.DataProtectedBySignature, publicKey: ByteArray, cardId: String
    ): Boolean {
        val firstData = IssuerDataToVerify(cardId, null, dataToWrite.counter, dataToWrite.data.size)
        val secondData = IssuerDataToVerify(cardId, dataToWrite.data, dataToWrite.counter)

        return verify(publicKey, dataToWrite.signature.startingSignature, firstData) &&
            verify(publicKey, dataToWrite.signature.finalizingSignature, secondData)
    }

    private fun writeFileData(
        session: CardSession,
        callback: (result: CompletionResult<WriteFileResponse>) -> Unit
    ) {
        if (mode == FileDataMode.WriteFile) {
            session.viewDelegate.onDelay(dataToWrite.data.size, offset, SINGLE_WRITE_SIZE)
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
                            offset += SINGLE_WRITE_SIZE
                            if (offset >= dataToWrite.data.size) {
                                mode = FileDataMode.ConfirmWritingFile
                            }
                            writeFileData(session, callback)
                            return@transceive
                        }
                        FileDataMode.ConfirmWritingFile -> {
                            callback(CompletionResult.Success(
                                WriteFileResponse(result.data.cardId, fileIndex)
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
                tlvBuilder.append(TlvTag.Size, dataToWrite.data.size)
                if (dataToWrite is DataToWrite.DataProtectedBySignature) {
                    tlvBuilder.append(TlvTag.IssuerDataSignature, dataToWrite.signature.startingSignature)
                    tlvBuilder.append(TlvTag.IssuerDataCounter, dataToWrite.counter)
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
                if (dataToWrite is DataToWrite.DataProtectedBySignature) {
                    tlvBuilder.append(TlvTag.IssuerDataSignature, dataToWrite.signature.finalizingSignature)
                } else {
                    tlvBuilder.append(TlvTag.CodeHash, dataToWrite.data.calculateSha256())
                    tlvBuilder.append(TlvTag.Pin2, environment.pin2?.value)
                }
                tlvBuilder.append(TlvTag.FileIndex, fileIndex)
            }
        }
        return CommandApdu(Instruction.WriteFileData, tlvBuilder.serialize())
    }

    private fun getDataToWrite(): ByteArray =
        dataToWrite.data.copyOfRange(offset, offset + calculatePartSize())

    private fun calculatePartSize(): Int {
        val bytesLeft = dataToWrite.data.size - offset
        return if (bytesLeft < SINGLE_WRITE_SIZE) bytesLeft else SINGLE_WRITE_SIZE
    }

    override fun deserialize(
        environment: SessionEnvironment,
        apdu: ResponseApdu
    ): WriteFileResponse {
        val tlvData = apdu.getTlvData() ?: throw TangemSdkError.DeserializeApduFailed()
        val decoder = TlvDecoder(tlvData)
        return WriteFileResponse(
            cardId = decoder.decode(TlvTag.CardId),
            fileIndex = decoder.decodeOptional(TlvTag.FileIndex)
        )
    }

    companion object {
        const val SINGLE_WRITE_SIZE = 1524
        const val MAX_SIZE = 48 * 1024
    }
}
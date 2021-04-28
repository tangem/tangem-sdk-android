package com.tangem.commands

import com.tangem.*
import com.tangem.commands.common.card.Card
import com.tangem.commands.common.card.CardStatus
import com.tangem.commands.common.card.masks.SigningMethod
import com.tangem.commands.wallet.WalletIndex
import com.tangem.commands.wallet.WalletStatus
import com.tangem.commands.wallet.addTlvData
import com.tangem.common.CompletionResult
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag
import com.tangem.crypto.sign
import com.tangem.json.BaseJsonRunnableAdapter
import com.tangem.json.CommandParams
import com.tangem.tasks.PreflightReadSettings

/**
 * @param cardId CID, Unique Tangem card ID number
 * @param signatures Signed hashes (array of resulting signatures)
 * @param walletRemainingSignatures Remaining number of sign operations before the wallet will stop signing transactions.
 * @param walletSignedHashes Total number of signed single hashes returned by the card in sign command responses.
 * Sums up array elements within all SIGN commands
 */
class SignResponse(
    val cardId: String,
    val signatures: List<ByteArray>,
    val walletRemainingSignatures: Int,
    val walletSignedHashes: Int?
) : CommandResponse

/**
 * Signs transaction hashes using a wallet private key, stored on the card.
 *
 * Note: Wallet index works only on COS v.4.0 and higher. For previous version index will be ignored
 * @property hashes Array of transaction hashes.
 * @property walletIndex Index to wallet for interaction.
 * @property cardId CID, Unique Tangem card ID number
 */
class SignCommand(
    private val hashes: Array<ByteArray>,
    private val walletIndex: WalletIndex
) : Command<SignResponse>() {

    override val requiresPin2 = true

    override fun preflightReadSettings(): PreflightReadSettings = PreflightReadSettings.ReadWallet(walletIndex)

    private val hashSizes = if (hashes.isNotEmpty()) hashes.first().size else 0
    private val hashesChunked = hashes.asList().chunked(CHUNK_SIZE)
    private var environment: SessionEnvironment? = null

    private val signatures = mutableListOf<ByteArray>()
    private val currentChunkNumber: Int
        get() = signatures.size / CHUNK_SIZE

    override fun run(session: CardSession, callback: (result: CompletionResult<SignResponse>) -> Unit) {
        sign(session, callback)
    }

    private fun sign(session: CardSession, callback: (result: CompletionResult<SignResponse>) -> Unit) {
        environment = session.environment
        transceive(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    signatures.addAll(result.data.signatures)
                    if (signatures.size == hashes.size) {
                        val finalResponse = SignResponse(
                            cardId = result.data.cardId,
                            signatures = signatures.toList(),
                            walletSignedHashes = result.data.walletSignedHashes,
                            walletRemainingSignatures = result.data.walletRemainingSignatures
                        )
                        callback(CompletionResult.Success(finalResponse))
                    } else {
                        sign(session, callback)
                    }
                }
                is CompletionResult.Failure -> callback(result)
            }
        }
    }

    override fun performPreCheck(card: Card): TangemSdkError? {
        if (card.status == CardStatus.NotPersonalized) {
            return TangemSdkError.NotPersonalized()
        }
        if (card.isActivated) {
            return TangemSdkError.NotActivated()
        }

        val wallet = card.wallet(walletIndex) ?: return TangemSdkError.WalletNotFound()

        when (wallet.status) {
            WalletStatus.Empty -> return TangemSdkError.WalletIsNotCreated()
            WalletStatus.Purged -> return TangemSdkError.WalletIsPurged()
        }

        if (card.firmwareVersion < FirmwareConstraints.DeprecationVersions.walletRemainingSignatures &&
            wallet.remainingSignatures == 0) {
            return TangemSdkError.NoRemainingSignatures()
        }
        if (card.firmwareVersion < FirmwareConstraints.AvailabilityVersions.pauseBeforePin2) {
            environment?.enableMissingSecurityDelay = true
        }
        if (card.signingMethods?.contains(SigningMethod.SignHash) != true) {
            return TangemSdkError.SignHashesNotAvailable()
        }
        if (hashSizes == 0) {
            return TangemSdkError.EmptyHashes()
        }
        if (hashes.any { it.size != hashSizes }) {
            return TangemSdkError.HashSizeMustBeEqual()
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
        val dataToSign = flattenHashes()
        val tlvBuilder = TlvBuilder()
        tlvBuilder.append(TlvTag.Pin, environment.pin1?.value)
        tlvBuilder.append(TlvTag.Pin2, environment.pin2?.value)
        tlvBuilder.append(TlvTag.CardId, environment.card?.cardId)
        tlvBuilder.append(TlvTag.TransactionOutHashSize, byteArrayOf(hashSizes.toByte()))
        tlvBuilder.append(TlvTag.TransactionOutHash, dataToSign)
        tlvBuilder.append(TlvTag.Cvc, environment.cvc)
        tlvBuilder.append(TlvTag.Cvc, environment.cvc)
        walletIndex.addTlvData(tlvBuilder)

        addTerminalSignature(environment, dataToSign, tlvBuilder)
        return CommandApdu(Instruction.Sign, tlvBuilder.serialize())
    }

    private fun flattenHashes(): ByteArray {
        return hashesChunked[currentChunkNumber].reduce { arr1, arr2 -> arr1 + arr2 }
    }

    /**
     * Application can optionally submit a public key Terminal_PublicKey in [SignCommand].
     * Submitted key is stored by the Tangem card if it differs from a previous submitted Terminal_PublicKey.
     * The Tangem card will not enforce security delay if [SignCommand] will be called with
     * TerminalTransactionSignature parameter containing a correct signature of raw data to be signed made with TerminalPrivateKey
     * (this key should be generated and securily stored by the application).
     */
    private fun addTerminalSignature(
        environment: SessionEnvironment, dataToSign: ByteArray, tlvBuilder: TlvBuilder
    ) {
        environment.terminalKeys?.let { terminalKeyPair ->
            val signedData = dataToSign.sign(terminalKeyPair.privateKey)
            tlvBuilder.append(TlvTag.TerminalTransactionSignature, signedData)
            tlvBuilder.append(TlvTag.TerminalPublicKey, terminalKeyPair.publicKey)
        }
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): SignResponse {
        val tlvData = apdu.getTlvData() ?: throw TangemSdkError.DeserializeApduFailed()

        val decoder = TlvDecoder(tlvData)
        val signature: ByteArray = decoder.decode(TlvTag.WalletSignature)
        val splittedSignatures = splitSignedSignature(signature, getChunk().count())

        return SignResponse(
            cardId = decoder.decode(TlvTag.CardId),
            signatures = splittedSignatures,
            walletRemainingSignatures = decoder.decodeOptional(TlvTag.WalletRemainingSignatures)
                ?: 99999, // In COS v.4.0 remaining signatures was removed
            walletSignedHashes = decoder.decodeOptional(TlvTag.WalletSignedHashes)
        )
    }

    private fun getChunk(): IntRange {
        val from = currentChunkNumber * CHUNK_SIZE
        val to = kotlin.math.min(from + CHUNK_SIZE, hashes.size)
        return from until to
    }

    private fun splitSignedSignature(signature: ByteArray, numberOfSignatures: Int): List<ByteArray> {
        val signatures = mutableListOf<ByteArray>()
        val signatureSize = signature.size / numberOfSignatures
        for (index in 0 until numberOfSignatures) {
            val offsetMin = index * signatureSize
            val offsetMax = offsetMin + signatureSize
            val sig = signature.copyOfRange(offsetMin, offsetMax)
            signatures.add(sig)
        }

        return signatures
    }

    companion object {
        const val CHUNK_SIZE = 10
    }

    class JsonAdapter : BaseJsonRunnableAdapter<SignParams, SignResponse>() {
        override fun initParams(): SignParams = convertJsonToParamsModel()

        override fun createRunnable(): CardSessionRunnable<SignResponse> {
            val hashes = params.hashes.map { it.hexToBytes() }.toTypedArray()
            return SignCommand(hashes, WalletIndex.Index(params.walletIndex))
        }

        companion object {
            const val METHOD = "SIGN_COMMAND"
        }
    }

    data class SignParams(val hashes: List<String>, val walletIndex: Int) : CommandParams()

}
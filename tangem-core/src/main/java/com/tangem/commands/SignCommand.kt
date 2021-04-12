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
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag
import com.tangem.crypto.sign
import com.tangem.tasks.PreflightReadSettings

/**
 * @param cardId CID, Unique Tangem card ID number
 * @param signature Signed hashes (array of resulting signatures)
 * @param walletRemainingSignatures Remaining number of sign operations before the wallet will stop signing transactions.
 * @param walletSignedHashes Total number of signed single hashes returned by the card in sign command responses.
 * Sums up array elements within all SIGN commands
 */
class SignResponse(
    val cardId: String,
    val signature: ByteArray,
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
    private var currentChunk = 0
    private val responses = mutableListOf<SignResponse>()
    private var environment: SessionEnvironment? = null

    override fun run(session: CardSession, callback: (result: CompletionResult<SignResponse>) -> Unit) {
        sign(session, callback)
    }

    private fun sign(session: CardSession, callback: (result: CompletionResult<SignResponse>) -> Unit) {
        environment = session.environment
        transceive(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    responses.add(result.data)
                    if (currentChunk == hashesChunked.lastIndex) {
                        val lastResponse = responses.last()
                        val finalResponse = SignResponse(
                            cardId = lastResponse.cardId,
                            signature = responses.fold(byteArrayOf()) { signatures, response ->
                                signatures + response.signature
                            },
                            walletSignedHashes = lastResponse.walletSignedHashes,
                            walletRemainingSignatures = lastResponse.walletRemainingSignatures
                        )
                        callback(CompletionResult.Success(finalResponse))
                    } else {
                        currentChunk += 1
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
        walletIndex?.addTlvData(tlvBuilder)

        addTerminalSignature(environment, dataToSign, tlvBuilder)
        return CommandApdu(Instruction.Sign, tlvBuilder.serialize())
    }

    private fun flattenHashes(): ByteArray {
        return hashesChunked[currentChunk].reduce { arr1, arr2 -> arr1 + arr2 }
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
        return SignResponse(
            cardId = decoder.decode(TlvTag.CardId),
            signature = decoder.decode(TlvTag.Signature),
            walletRemainingSignatures = decoder.decodeOptional(TlvTag.RemainingSignatures)
                ?: 99999, // In COS v.4.0 remaining signatures was removed
            walletSignedHashes = decoder.decodeOptional(TlvTag.WalletSignedHashes)
        )
    }

    companion object {
        const val CHUNK_SIZE = 10
    }
}
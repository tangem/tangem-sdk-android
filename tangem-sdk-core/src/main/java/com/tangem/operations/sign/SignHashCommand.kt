package com.tangem.operations.sign

import com.squareup.moshi.JsonClass
import com.tangem.common.CompletionResult
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.CompletionCallback
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.operations.CommandResponse

/**
 * Response for [SignHashCommand].
 *
 * @property cardId CID, Unique Tangem card ID number
 * @property walletPublicKey Wallet derivation key
 * @property signature Signed hash
 * @property totalSignedHashes Total number of signed  hashes returned by the wallet since its creation. COS: 1.16+
 */
@JsonClass(generateAdapter = true)
data class SignHashResponse(
    val cardId: String,
    val walletPublicKey: ByteArray,
    val signature: ByteArray,
    val totalSignedHashes: Int?,
) : CommandResponse

/**
 * Signs transaction hash using a wallet private key, stored on the card.
 * @property hash: Transaction hash for sign by card.
 * @property walletPublicKey: Public key of the wallet, using for sign.
 * @property derivationPath: Derivation path of the wallet. Optional. COS v. 4.28 and higher,
 */
class SignHashCommand(
    private val hash: ByteArray,
    private val walletPublicKey: ByteArray,
    private val derivationPath: DerivationPath? = null,
) : CardSessionRunnable<SignHashResponse> {

    override fun run(session: CardSession, callback: CompletionCallback<SignHashResponse>) {
        val signCommand = SignCommand(arrayOf(hash), walletPublicKey, derivationPath)
        signCommand.run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    val response = SignHashResponse(
                        cardId = result.data.cardId,
                        walletPublicKey = walletPublicKey,
                        signature = result.data.signatures[0],
                        totalSignedHashes = result.data.totalSignedHashes,
                    )
                    callback(CompletionResult.Success(response))
                }
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }
        }
    }
}
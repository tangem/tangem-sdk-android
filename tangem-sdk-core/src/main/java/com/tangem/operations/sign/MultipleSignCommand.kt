package com.tangem.operations.sign

import com.tangem.common.CompletionResult
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.TangemSdkError

/**
 * Signs transaction hash using a wallet private key, stored on the card.
 * @property dataToSign: Transaction hash for sign by card.
 * @property walletPublicKey: Public key of the wallet, using for sign.
 */
class MultipleSignCommand(
    private val dataToSign: List<SignData>,
    private val walletPublicKey: ByteArray,
) : CardSessionRunnable<List<SignHashResponse>> {

    override fun run(session: CardSession, callback: CompletionCallback<List<SignHashResponse>>) {
        val data = mutableListOf<SignHashResponse>()
        val hashesStack = ArrayDeque(dataToSign)
        try {
            val signData = hashesStack.removeLastOrNull()
            if (signData == null) {
                callback(CompletionResult.Failure(TangemSdkError.EmptyHashes()))
                return
            }
            runSign(
                signData = signData,
                hashes = hashesStack,
                result = data,
                session = session,
                callback = callback,
            )
        } catch (error: Throwable) {
            callback(CompletionResult.Failure(TangemSdkError.ExceptionError(error)))
        }
    }

    private fun runSign(
        signData: SignData,
        hashes: ArrayDeque<SignData>,
        result: MutableList<SignHashResponse>,
        session: CardSession,
        callback: CompletionCallback<List<SignHashResponse>>,
    ) {
        val hash = signData.hash
        val derivationPath = signData.derivationPath
        val signCommand = SignCommand(arrayOf(hash), walletPublicKey, derivationPath)

        signCommand.run(session) { res ->
            when (res) {
                is CompletionResult.Success -> {
                    val response = SignHashResponse(
                        cardId = res.data.cardId,
                        walletPublicKey = signData.publicKey,
                        signature = res.data.signatures[0],
                        totalSignedHashes = res.data.totalSignedHashes,
                    )
                    result.add(response)
                    val newSignData = hashes.removeLastOrNull()
                    if (newSignData != null) {
                        runSign(newSignData, hashes, result, session, callback)
                    } else {
                        callback(CompletionResult.Success(result))
                    }
                }
                is CompletionResult.Failure -> callback(CompletionResult.Failure(res.error))
            }
        }
    }
}
package com.tangem.commands.file

import com.tangem.CardSession
import com.tangem.CardSessionRunnable
import com.tangem.KeyPair
import com.tangem.TangemSdkError
import com.tangem.common.CompletionResult
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.extensions.toByteArray
import com.tangem.crypto.sign

/**
 * Task uses commands that are in development and subject to future changes
 */
class WriteFileDataTask(
        private val data: ByteArray,
        private val issuerKeys: KeyPair
) : CardSessionRunnable<WriteFileDataResponse> {

    override val requiresPin2 = false

    override fun run(session: CardSession, callback: (result: CompletionResult<WriteFileDataResponse>) -> Unit) {
        val command = ReadFileDataCommand()
        command.run(session) { readResponse ->
            when (readResponse) {
                is CompletionResult.Failure -> callback(CompletionResult.Failure(readResponse.error))
                is CompletionResult.Success -> {
                    val counter = (readResponse.data.fileDataCounter ?: 0) + 1
                    val cardId = session.environment.card?.cardId
                    if (cardId == null) {
                        callback(CompletionResult.Failure(TangemSdkError.CardError()))
                        return@run
                    }
                    val writeCommand = WriteFileDataCommand(
                            data,
                            getStartingSignature(data, counter, cardId),
                            getFinalizingSignature(data, counter, cardId),
                            counter, issuerKeys.publicKey
                    )
                    writeCommand.run(session) { result -> callback(result) }
                }
            }
        }
    }

    private fun getStartingSignature(data: ByteArray, counter: Int, cardId: String): ByteArray {
        return (cardId.hexToBytes() + counter.toByteArray(4) + data.size.toByteArray(2))
                .sign(issuerKeys.privateKey)
    }

    private fun getFinalizingSignature(data: ByteArray, counter: Int, cardId: String): ByteArray {
        return (cardId.hexToBytes() + data + counter.toByteArray(4))
                .sign(issuerKeys.privateKey)
    }

}
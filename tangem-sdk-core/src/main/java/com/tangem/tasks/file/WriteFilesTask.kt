package com.tangem.tasks.file

import com.squareup.moshi.JsonClass
import com.tangem.CardSession
import com.tangem.CardSessionRunnable
import com.tangem.TangemSdkError
import com.tangem.commands.CommandResponse
import com.tangem.commands.file.FileData
import com.tangem.commands.file.WriteFileDataCommand
import com.tangem.common.CompletionResult
import com.tangem.common.extensions.guard

/**
 * This task allows to write multiple files to a card.
 * There are two secure ways to write files.
 * 1) Data can be signed by Issuer (the one specified on card during personalization) -
 * [FileData.DataProtectedBySignature].
 * 2) Data can be protected by Passcode (PIN2). [FileData.DataProtectedByPasscode] In this case,
 * Passcode (PIN2) is required for the command.
 *
 * @property data files to be written.
 */
@JsonClass(generateAdapter = true)
class WriteFilesResponse(
    val cardId: String,
    val fileIndices: List<Int>
) : CommandResponse

class WriteFilesTask(
    private val data: List<FileData>,
    private val overwriteAllFiles: Boolean = false
) : CardSessionRunnable<WriteFilesResponse> {

    override val requiresPin2 = data.any { it is FileData.DataProtectedByPasscode }

    private var currentIndex = 0
    private val savedFilesIndices = mutableListOf<Int>()

    override fun run(session: CardSession, callback: (result: CompletionResult<WriteFilesResponse>) -> Unit) {
        if (data.isEmpty()) {
            callback(CompletionResult.Success(WriteFilesResponse("", listOf())))
            return
        }

        if (overwriteAllFiles) {
            deleteFiles(session, callback)
        } else {
            writeFiles(session, callback)
        }
    }

    private fun deleteFiles(session: CardSession, callback: (result: CompletionResult<WriteFilesResponse>) -> Unit) {
        DeleteFilesTask().run(session) { result ->
            when (result) {
                is CompletionResult.Success -> writeFiles(session, callback)
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }
        }
    }

    private fun writeFiles(session: CardSession, callback: (result: CompletionResult<WriteFilesResponse>) -> Unit) {
        val card = session.environment.card.guard {
            callback(CompletionResult.Failure(TangemSdkError.CardError()))
            return
        }

        val fileData = data[currentIndex]
        WriteFileDataCommand(fileData).run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    result.data.fileIndex?.let { savedFilesIndices.add(it) }
                    if (currentIndex == data.lastIndex) {
                        callback(CompletionResult.Success(WriteFilesResponse(card.cardId, savedFilesIndices)))
                    } else {
                        currentIndex += 1
                        writeFiles(session, callback)
                    }
                }
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }
        }
    }
}
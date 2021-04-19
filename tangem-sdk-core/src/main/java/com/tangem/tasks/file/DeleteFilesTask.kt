package com.tangem.tasks.file

import com.tangem.CardSession
import com.tangem.CardSessionRunnable
import com.tangem.TangemSdkError
import com.tangem.commands.file.DeleteFileCommand
import com.tangem.commands.file.DeleteFileResponse
import com.tangem.commands.file.WriteFileDataCommand
import com.tangem.common.CompletionResult

/**
 * This task allows to delete multiple or all files written to the card with [WriteFileDataCommand].
 * Passcode (PIN2) is required to delete the files.
 *
 * @property filesIndices indices of files to be deleted. If [filesIndices] are not provided,
 * then all files will be deleted.
 */
class DeleteFilesTask(
        private val filesIndices: List<Int>? = null
) : CardSessionRunnable<DeleteFileResponse> {

    override val requiresPin2 = true

    override fun run(
            session: CardSession, callback: (result: CompletionResult<DeleteFileResponse>) -> Unit
    ) {
        if (filesIndices == null) {
            deleteAllFiles(session, callback)
        } else {
            deleteFiles(filesIndices.sorted(), session, callback)
        }
    }

    private fun deleteAllFiles(
            session: CardSession, callback: (result: CompletionResult<DeleteFileResponse>) -> Unit
    ) {
        DeleteFileCommand(0).run(session) { result ->
            when (result) {
                is CompletionResult.Success -> deleteAllFiles(session, callback)
                is CompletionResult.Failure ->
                    if (result.error is TangemSdkError.ErrorProcessingCommand) {
                        callback(CompletionResult.Success(
                                DeleteFileResponse(session.environment.card?.cardId ?: "")))
                    } else {
                        callback(CompletionResult.Failure(result.error))
                    }
            }
        }
    }

    private fun deleteFiles(
            filesIndex: List<Int>,
            session: CardSession, callback: (result: CompletionResult<DeleteFileResponse>) -> Unit
    ) {
        val index = filesIndex.last()
        DeleteFileCommand(index).run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    val remainingIndex = filesIndex.dropLast(1)
                    if (remainingIndex.isEmpty()) {
                        callback(result)
                    } else {
                        deleteFiles(remainingIndex, session, callback)
                    }
                }
                is CompletionResult.Failure -> callback(result)
            }
        }
    }

}
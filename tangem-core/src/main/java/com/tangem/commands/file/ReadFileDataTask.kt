package com.tangem.commands.file

import com.tangem.CardSession
import com.tangem.CardSessionRunnable
import com.tangem.TangemSdkError
import com.tangem.commands.CommandResponse
import com.tangem.common.CompletionResult

class ReadFilesResponse(
        val files: List<File>
) : CommandResponse

data class File(
        val fileIndex: Int,
        val fileSettings: FileSettings?,
        val fileData: ByteArray
)

/**
 * Task uses commands that are in development and subject to future changes
 */
class ReadFileDataTask(
        private val readPrivateFiles: Boolean = false
) : CardSessionRunnable<ReadFilesResponse> {

    override val requiresPin2 = readPrivateFiles
    private var fileIndex = 0
    private val files = mutableListOf<File>()

    override fun run(session: CardSession, callback: (result: CompletionResult<ReadFilesResponse>) -> Unit) {
        performReadFileDataCommand(session, callback)
    }

    private fun performReadFileDataCommand(session: CardSession, callback: (result: CompletionResult<ReadFilesResponse>) -> Unit) {
        val command = ReadFileDataCommand(fileIndex, readPrivateFiles)
        command.run(session) { readResponse ->
            when (readResponse) {
                is CompletionResult.Failure -> {
                    if (readResponse.error is TangemSdkError.FileNotFound) {
                        callback(CompletionResult.Success(ReadFilesResponse(files)))
                    } else {
                        callback(CompletionResult.Failure(readResponse.error))
                    }
                }
                is CompletionResult.Success -> {
                    val newFile = File(
                            readResponse.data.fileIndex, readResponse.data.fileSettings,
                            readResponse.data.fileData
                    )
                    files.add(newFile)
                    fileIndex += 1
                    performReadFileDataCommand(session, callback)
                }
            }
        }
    }


}
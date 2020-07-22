package com.tangem.commands.file

import com.tangem.CardSession
import com.tangem.CardSessionRunnable
import com.tangem.Log
import com.tangem.TangemSdkError
import com.tangem.commands.CommandResponse
import com.tangem.common.CompletionResult

class ReadFilesResponse(
        val files: List<FileData>
) : CommandResponse

data class FileData(
        val fileIndex: Int,
        val fileData: ByteArray
)

class ReadFileDataTask(
        private val readPrivateFiles: Boolean = false
) : CardSessionRunnable<ReadFilesResponse> {

    override val requiresPin2 = readPrivateFiles
    private var fileIndex = 0
    private val files = mutableListOf<FileData>()

    override fun run(session: CardSession, callback: (result: CompletionResult<ReadFilesResponse>) -> Unit) {
        performReadFileDataCommand(session, callback)
    }

    private fun performReadFileDataCommand(session: CardSession, callback: (result: CompletionResult<ReadFilesResponse>) -> Unit) {
        val command = ReadFileDataCommand(fileIndex, readPrivateFiles)
        command.run(session) { readResponse ->
            when (readResponse) {
                is CompletionResult.Failure -> {
                    if (readResponse.error is TangemSdkError.FileNotFound) {
                        Log.i("ReadFileDataTask", files.toString())
                        Log.i("ReadFileDataTask", files.size.toString())
                        callback(CompletionResult.Success(ReadFilesResponse(files)))
                    } else {
                        callback(CompletionResult.Failure(readResponse.error))
                    }
                }
                is CompletionResult.Success -> {
                    val newFile = FileData(readResponse.data.fileIndex, readResponse.data.fileData)
                    files.add(newFile)
                    fileIndex += 1
                    performReadFileDataCommand(session, callback)
                }
            }
        }
    }


}
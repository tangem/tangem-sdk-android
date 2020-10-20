package com.tangem.tasks.file

import com.tangem.CardSession
import com.tangem.CardSessionRunnable
import com.tangem.TangemSdkError
import com.tangem.commands.CommandResponse
import com.tangem.commands.file.FileSettings
import com.tangem.commands.file.ReadFileDataCommand
import com.tangem.commands.file.WriteFileDataCommand
import com.tangem.common.CompletionResult

class ReadFilesResponse(
        val files: List<File>
) : CommandResponse

data class File(
        val fileIndex: Int,
        val fileSettings: FileSettings?,
        val fileData: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as File

        if (fileIndex != other.fileIndex) return false
        if (fileSettings != other.fileSettings) return false
        if (!fileData.contentEquals(other.fileData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileIndex
        result = 31 * result + (fileSettings?.hashCode() ?: 0)
        result = 31 * result + fileData.contentHashCode()
        return result
    }
}

/**
 * This task allows to read multiple files written to the card with [WriteFileDataCommand].
 * If the files are private, then Passcode (PIN2) is required to read the files.
 *
 * @property readPrivateFiles if set to true, then the task will read private files,
 * for which it requires PIN2. Otherwise only public files can be read.
 * @property indices indices of files to be read. If not provided, the task will read and return
 * all files from a card that satisfy the access level condition (either only public or private and public).
 */
class ReadFilesTask(
        private val readPrivateFiles: Boolean = false,
        private val indices: List<Int>? = null
) : CardSessionRunnable<ReadFilesResponse> {

    override val requiresPin2 = readPrivateFiles
    private var index = 0
    private val files = mutableListOf<File>()

    override fun run(session: CardSession, callback: (result: CompletionResult<ReadFilesResponse>) -> Unit) {
        if (indices.isNullOrEmpty()) {
            readAllFiles(session, callback)
        } else {
            readSpecifiedFiles(indices, session, callback)
        }
    }

    private fun readAllFiles(session: CardSession, callback: (result: CompletionResult<ReadFilesResponse>) -> Unit) {
        val command = ReadFileDataCommand(index, readPrivateFiles)
        command.run(session) { result ->
            when (result) {
                is CompletionResult.Failure -> {
                    if (result.error is TangemSdkError.FileNotFound) {
                        callback(CompletionResult.Success(ReadFilesResponse(files)))
                    } else {
                        callback(CompletionResult.Failure(result.error))
                    }
                }
                is CompletionResult.Success -> {
                    val data = result.data
                    val newFile = File(data.fileIndex, data.fileSettings, data.fileData)
                    files.add(newFile)

                    index = indices?.first() ?: data.fileIndex + 1
                    readAllFiles(session, callback)
                }
            }
        }
    }

    private fun readSpecifiedFiles(
            indices: List<Int>,
            session: CardSession, callback: (result: CompletionResult<ReadFilesResponse>) -> Unit
    ) {
        val command = ReadFileDataCommand(indices[index], readPrivateFiles)
        command.run(session) { result ->
            when (result) {
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                is CompletionResult.Success -> {
                    val data = result.data
                    val newFile = File(data.fileIndex, data.fileSettings, data.fileData)
                    files.add(newFile)

                    if (index == indices.lastIndex) {
                        callback(CompletionResult.Success(ReadFilesResponse(files)))
                        return@run
                    }

                    index += 1
                    readSpecifiedFiles(indices, session, callback)
                }
            }
        }
    }
}
package com.tangem.tasks.file

import com.tangem.CardSession
import com.tangem.CardSessionRunnable
import com.tangem.commands.file.FileData
import com.tangem.commands.file.WriteFileDataCommand
import com.tangem.commands.file.WriteFileDataResponse
import com.tangem.common.CompletionResult

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
class WriteFilesTask(
        private val data: List<FileData>
) : CardSessionRunnable<WriteFileDataResponse> {

    override val requiresPin2 = data.any { it is FileData.DataProtectedByPasscode }

    private var currentIndex = 0

    override fun run(session: CardSession, callback: (result: CompletionResult<WriteFileDataResponse>) -> Unit) {
       writeFiles(session, callback)
    }

    private fun writeFiles(session: CardSession, callback: (result: CompletionResult<WriteFileDataResponse>) -> Unit) {
        val fileData = data[currentIndex]
        WriteFileDataCommand(fileData).run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    if (currentIndex == data.lastIndex) {
                        callback(result)
                    } else {
                        currentIndex += 1
                        writeFiles(session, callback)
                    }
                }
                is CompletionResult.Failure -> callback(result)
            }
        }
    }
}
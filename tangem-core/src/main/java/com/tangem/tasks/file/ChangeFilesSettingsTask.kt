package com.tangem.tasks.file

import com.tangem.CardSession
import com.tangem.CardSessionRunnable
import com.tangem.commands.file.*
import com.tangem.common.CompletionResult

/**
 * This task allows to change settings of multiple files written to the card with [WriteFileDataCommand].
 * Passcode (PIN2) is required for this operation.
 * [FileSettings] change access level to a file - it can be [FileSettings.Private],
 * accessible only with PIN2, or [FileSettings.Public], accessible without PIN2
 *
 * @property changes contains list of [FileSettingsChange] -
 * indices of files that are to be changed and desired settings.
 */
class ChangeFilesSettingsTask(
        private val changes: List<FileSettingsChange>
) : CardSessionRunnable<ChangeFileSettingsResponse> {

    override val requiresPin2 = true

    private var currentIndex = 0

    override fun run(
            session: CardSession, callback: (result: CompletionResult<ChangeFileSettingsResponse>) -> Unit
    ) {
        changeSettings(session, callback)
    }

    private fun changeSettings(
            session: CardSession, callback: (result: CompletionResult<ChangeFileSettingsResponse>) -> Unit
    ) {
        ChangeFileSettingsCommand(changes[currentIndex]).run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    if (currentIndex == changes.lastIndex) {
                        callback(result)
                    } else {
                        currentIndex += 1
                        changeSettings(session, callback)
                    }
                }
                is CompletionResult.Failure -> callback(result)
            }
        }
    }
}
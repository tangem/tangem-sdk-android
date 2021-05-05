package com.tangem.json

import com.tangem.CardSession
import com.tangem.CardSessionRunnable
import com.tangem.commands.CommandResponse
import com.tangem.commands.common.jsonConverter.MoshiJsonConverter
import com.tangem.common.CompletionResult

/**
[REDACTED_AUTHOR]
 */
abstract class JsonRunnableAdapter<R : CommandResponse>(
    protected val jsonConverter: MoshiJsonConverter,
    protected val jsonData: Map<String, Any>
) : CardSessionRunnable<CommandResponse> {

    override val requiresPin2: Boolean
        get() = runnable.requiresPin2


    private val runnable: CardSessionRunnable<R> = createRunnable()

    protected abstract fun createRunnable(): CardSessionRunnable<R>

    override fun run(session: CardSession, callback: (result: CompletionResult<CommandResponse>) -> Unit) {
        runnable.run(session) { handleCommandResult(it, callback) }
    }

    private fun handleCommandResult(
        commandResult: CompletionResult<R>,
        callback: (result: CompletionResult<CommandResponse>) -> Unit
    ) {
        when (commandResult) {
            is CompletionResult.Success -> {
                val convertedResponse = jsonConverter.toMap(commandResult.data)
                callback(CompletionResult.Success(JsonResponse(convertedResponse)))
            }
            is CompletionResult.Failure -> callback(CompletionResult.Failure(commandResult.error))
        }
    }

    protected inline fun <reified T> convertJsonToParamsModel(): T {
        val jsonParams = jsonConverter.toJson(jsonData["parameters"] ?: mapOf<String, Any>())
        return jsonConverter.fromJson(jsonParams)!!
    }

    private fun handleSuccess(data: CommandResponse, callback: (result: CompletionResult<CommandResponse>) -> Unit) {
        callback(CompletionResult.Success(JsonResponse(jsonConverter.toMap(data))))
    }
}
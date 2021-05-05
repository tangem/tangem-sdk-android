package com.tangem.json

import com.google.gson.reflect.TypeToken
import com.tangem.CardSession
import com.tangem.CardSessionRunnable
import com.tangem.commands.CommandResponse
import com.tangem.commands.common.ResponseConverter
import com.tangem.common.CompletionResult

/**
[REDACTED_AUTHOR]
 */
abstract class GsonRunnableAdapter<R : CommandResponse>(
    protected val jsonData: Map<String, Any>
) : CardSessionRunnable<CommandResponse> {

    override val requiresPin2: Boolean
        get() = runnable.requiresPin2

    protected val responseConverter: ResponseConverter = ResponseConverter()

    private val runnable: CardSessionRunnable<R> = createRunnable()

    abstract fun createRunnable(): CardSessionRunnable<R>

    override fun run(session: CardSession, callback: (result: CompletionResult<CommandResponse>) -> Unit) {
        runnable.run(session) { handleCommandResult(it, callback) }
    }

    private fun handleCommandResult(
        commandResult: CompletionResult<R>,
        callback: (result: CompletionResult<CommandResponse>) -> Unit
    ) {
        when (commandResult) {
            is CompletionResult.Success -> {
                val convertedResponse = convertResponseToJson(commandResult.data)
                callback(CompletionResult.Success(JsonResponse(convertedResponse)))
            }
            is CompletionResult.Failure -> callback(CompletionResult.Failure(commandResult.error))
        }
    }

    protected inline fun <reified T> convertJsonToParamsModel(): T {
        val gson = responseConverter.gson
        val parametersJson = gson.toJson(jsonData["parameters"])
        return gson.fromJson(parametersJson, T::class.java)
    }

    private fun convertResponseToJson(response: CommandResponse): Map<String, Any> {
        val typeToken = object : TypeToken<Map<String, Any>>() {}.type
        val convertedResponse = responseConverter.convertResponse(response)
        return responseConverter.gson.fromJson(convertedResponse, typeToken)
    }

    private fun handleSuccess(data: CommandResponse, callback: (result: CompletionResult<CommandResponse>) -> Unit) {
        val convertedResponse = convertResponseToJson(data)
        callback(CompletionResult.Success(JsonResponse(convertedResponse)))
    }
}
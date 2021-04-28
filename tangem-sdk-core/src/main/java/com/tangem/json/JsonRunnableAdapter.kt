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
interface JsonRunnableAdapter : CardSessionRunnable<CommandResponse> {
    fun setJson(json: Map<String, Any>)
}

abstract class BaseJsonRunnableAdapter<P : CommandParams, R : CommandResponse>(
    protected val responseConverter: ResponseConverter = ResponseConverter()
) : JsonRunnableAdapter {

    override val requiresPin2: Boolean = false

    protected val jsonData = mutableMapOf<String, Any>()
    protected lateinit var params: P

    override fun setJson(json: Map<String, Any>) {
        jsonData.clear()
        jsonData + json
        params = initParams()
    }

    protected abstract fun initParams(): P

    override fun run(session: CardSession, callback: (result: CompletionResult<CommandResponse>) -> Unit) {
        createRunnable().run(session) { handleCommandResult(it, callback) }
    }

    abstract fun createRunnable(): CardSessionRunnable<R>

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

    protected open fun convertResponseToJson(response: CommandResponse): Map<String, Any> {
        val typeToken = object : TypeToken<Map<String, Any>>() {}.type
        val convertedResponse = responseConverter.convertResponse(response)
        return responseConverter.gson.fromJson(convertedResponse, typeToken)
    }

    protected fun handleSuccess(data: CommandResponse, callback: (result: CompletionResult<CommandResponse>) -> Unit) {
        val convertedResponse = convertResponseToJson(data)
        callback(CompletionResult.Success(JsonResponse(convertedResponse)))
    }
}
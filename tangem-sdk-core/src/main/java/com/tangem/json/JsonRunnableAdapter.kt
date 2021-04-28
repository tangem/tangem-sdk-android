package com.tangem.json

import com.google.gson.reflect.TypeToken
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

abstract class BaseJsonRunnableAdapter<T : CommandParams>(
    protected val responseConverter: ResponseConverter = ResponseConverter()
) : JsonRunnableAdapter {

    override val requiresPin2: Boolean = false

    protected val jsonData = mutableMapOf<String, Any>()

    protected lateinit var params: T

    override fun setJson(json: Map<String, Any>) {
        jsonData.clear()
        jsonData + json
        params = initParams()
    }

    protected abstract fun initParams(): T

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
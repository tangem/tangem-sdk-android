package com.tangem.json

import com.tangem.CardSessionRunnable
import com.tangem.TangemError
import com.tangem.commands.CommandResponse
import com.tangem.commands.SignCommand
import com.tangem.commands.common.ResponseConverter
import com.tangem.common.CompletionResult
import java.lang.Exception

/**
[REDACTED_AUTHOR]
 */
interface JsonAdaptersFactory {
    fun register(name: String, runnableAdapter: JsonRunnableAdapter<*>)
    fun get(json: Map<String, Any>): JsonRunnableAdapter<*>?
}

interface JsonRunnableAdapter<T : CommandResponse> {
    fun buildRunnable(json: Map<String, Any>): CompletionResult<CardSessionRunnable<T>>
    fun convertResponse(response: T): Map<String, Any>
}

sealed class JsonError(final override val code: Int): Exception(), TangemError {
    override var customMessage: String = code.toString()
    override val messageResId: Int? = null

    class NoSuchParametersError(override var customMessage: String): JsonError(123123)
    class ParameterCastError(override var customMessage: String): JsonError(321321)
}

class DefaultRunnableFactory(
    private val responseConverter: ResponseConverter
) : JsonAdaptersFactory {

    private val builders = mutableMapOf<String, JsonRunnableAdapter<*>>()

    init {
        register(SignCommand.METHOD, SignCommand.SignJsonAdapter(responseConverter))
    }

    override fun register(name: String, runnableAdapter: JsonRunnableAdapter<*>) {
        builders[name] = runnableAdapter
    }

    override fun get(json: Map<String, Any>): JsonRunnableAdapter<*>? {
        return json["name"]?.let { builders[it] }
    }
}
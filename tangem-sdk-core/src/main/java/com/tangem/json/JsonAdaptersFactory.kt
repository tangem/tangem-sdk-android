package com.tangem.json

import com.tangem.commands.CommandResponse
import com.tangem.commands.SignCommand
import com.tangem.tasks.ScanTask
import java.lang.Exception

/**
[REDACTED_AUTHOR]
 */
class JsonResponse(val response: Map<String, Any>) : CommandResponse

interface JsonAdaptersFactory {
    fun register(name: String, creature: () -> JsonRunnableAdapter)
    fun get(json: Map<String, Any>): JsonRunnableAdapter?
}

class DefaultRunnableFactory : JsonAdaptersFactory {

    private val commandAdapters = mutableMapOf<String, () -> JsonRunnableAdapter>()

    init {
        register(SignCommand.JsonAdapter.METHOD) { SignCommand.JsonAdapter() }
        register(ScanTask.JsonAdapter.METHOD) { ScanTask.JsonAdapter() }
    }

    override fun register(name: String, lazyAdapter: () -> JsonRunnableAdapter) {
        commandAdapters[name] = lazyAdapter
    }

    override fun get(json: Map<String, Any>): JsonRunnableAdapter? {
        return try {
            val methodName = json["name"] ?: return null
            commandAdapters[methodName]?.invoke()?.apply { setJson(json) }
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }
}
package com.tangem.json

import com.tangem.commands.CommandResponse
import com.tangem.commands.SignCommand
import com.tangem.tasks.ScanTask
import java.lang.Exception

/**
[REDACTED_AUTHOR]
 */
class JsonResponse(val response: Map<String, Any>) : CommandResponse

class JsonAdaptersFactory {

    private val commandAdapters = mutableMapOf<String, () -> JsonRunnableAdapter<*>>()

    init {
        registerAdapter(SignCommand.JsonAdapter.METHOD) { SignCommand.JsonAdapter() }
        registerAdapter(ScanTask.JsonAdapter.METHOD) { ScanTask.JsonAdapter() }
    }

    fun registerAdapter(name: String, lazyAdapter: () -> JsonRunnableAdapter<*>) {
        commandAdapters[name] = lazyAdapter
    }

    fun createFrom(json: Map<String, Any>): JsonRunnableAdapter<*>? {
        return try {
            val methodName = json["method"] ?: return null
            commandAdapters[methodName]?.invoke()?.apply { initWithJson(json) }
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }
}
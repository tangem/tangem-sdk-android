package com.tangem.json

import com.tangem.commands.CommandResponse
import com.tangem.commands.SignCommand
import com.tangem.extentions.toSnakeCase
import com.tangem.tasks.ScanTask
import kotlin.reflect.KClass

/**
[REDACTED_AUTHOR]
 */
class JsonResponse(val response: Map<String, Any>) : CommandResponse

class JsonAdaptersFactory {

    private val commandAdapters = mutableMapOf<String, KClass<*>>()

    init {
        registerAdapter(SignCommand::class)
        registerAdapter(ScanTask::class)
    }

    fun registerAdapter(adapterClass: KClass<*>) {
        val adapterName = adapterClass.simpleName?.toSnakeCase()?.toUpperCase() ?: return

        commandAdapters[adapterName] = adapterClass
    }

    fun createFrom(json: Map<String, Any>): GsonRunnableAdapter<*>? {
        return try {
            val methodName = json["method"] ?: return null

            commandAdapters[methodName]?.nestedClasses?.forEach {
                if (it.simpleName == "JsonAdapter") {
                    val constructor = it.constructors.filter { it.parameters.size == 1 }
                    val appropriateConstructor = constructor.firstOrNull()
                    return appropriateConstructor?.call(json) as? GsonRunnableAdapter<*>
                }
            }
            null
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }
}
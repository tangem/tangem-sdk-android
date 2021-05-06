package com.tangem.json

import com.tangem.commands.CommandResponse
import com.tangem.commands.SignCommand
import com.tangem.commands.common.jsonConverter.MoshiJsonConverter
import com.tangem.extentions.toSnakeCase
import com.tangem.tasks.ScanTask
import kotlin.reflect.KClass

/**
[REDACTED_AUTHOR]
 */
class JsonResponse(val response: Map<String, Any>) : CommandResponse

class JsonAdaptersFactory {

    val jsonConverter = MoshiJsonConverter.tangemSdkJsonConverter()

    private val commandAdapters = mutableMapOf<String, KClass<*>>()

    init {
        registerAdapter(SignCommand::class)
        registerAdapter(ScanTask::class)
    }

    fun registerAdapter(adapterClass: KClass<*>) {
        val adapterName = adapterClass.simpleName?.toSnakeCase()?.toUpperCase() ?: return

        commandAdapters[adapterName] = adapterClass
    }

    fun createFrom(json: Map<String, Any>): JsonRunnableAdapter<*>? {
        return try {
            if (!checkForStandard(json)) return null
            val methodName = json["method"] ?: return null

            commandAdapters[methodName]?.nestedClasses?.forEach {
                if (it.simpleName == "JsonAdapter") {
                    val constructor = it.constructors.filter { it.parameters.size == 2 }
                    val appropriateConstructor = constructor.firstOrNull()
                    return appropriateConstructor?.call(jsonConverter, json) as? JsonRunnableAdapter<*>
                }
            }
            null
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }

    private fun checkForStandard(json: Map<String, Any>): Boolean {
        return json.containsKey("method") && json.containsKey("parameters")
    }
}
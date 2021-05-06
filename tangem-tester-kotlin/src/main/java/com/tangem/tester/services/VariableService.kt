package com.tangem.tester.services

import com.tangem.commands.CommandResponse
import com.tangem.commands.common.jsonConverter.ResponseConverter
import org.json.JSONArray
import org.json.JSONObject

/**
[REDACTED_AUTHOR]
 */
object VariableService {

    private val stepResults = mutableMapOf<String, Map<String, Any?>>()
    private val converter = ResponseConverter()

    fun registerResult(name: String, result: CommandResponse) {
        stepResults[name] = JSONObject(converter.toJson(result)).toMap()
    }

    fun getValue(name: String, pointer: Any?): Any? {
        return getFromContext(pointer, stepResults[name])
    }

    private fun getFromContext(pointer: Any?, context: Any?): Any? {
        var nonNullPointer = pointer ?: return null

        if (isVariable(nonNullPointer)) nonNullPointer = attachParentKeyIfNeeded(nonNullPointer)
        return Interpreter.interpret(nonNullPointer, context)
    }

    private fun isVariable(valueDescription: Any?): Boolean {
        val stringValue = valueDescription as? String ?: return false

        return stringValue.contains("{") && stringValue.contains("}")
    }

    private fun attachParentKeyIfNeeded(valueDescription: Any): Any {
        return when {
            valueDescription !is String -> valueDescription
            valueDescription[1] == '#' -> valueDescription
            else -> valueDescription.insertAt(1, "#parent.")
        }
    }
}

class Interpreter {
    companion object {
        fun interpret(value: Any?, context: Any?): Any? {
            val nonNullValue = value ?: return null

            return nonNullValue
        }
    }
}

fun String.insertAt(position: Int, what: String): String {
    return this.replace("^(.{$position})", "$1$what");
}

fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith {
    when (val value = this[it]) {
        is JSONArray -> {
            val map = (0 until value.length()).associate { Pair(it.toString(), value[it]) }
            JSONObject(map).toMap().values.toList()
        }
        is JSONObject -> value.toMap()
        JSONObject.NULL -> null
        else -> value
    }
}
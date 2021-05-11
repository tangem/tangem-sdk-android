package com.tangem.tester.variables

import com.tangem.json.JsonResponse
import com.tangem.tester.SourceMap
import com.tangem.tester.common.isNumber
import com.tangem.tester.common.safeSplit
import java.util.regex.Pattern

/**
[REDACTED_AUTHOR]
 */
object VariableService {
    private val variablePattern = Pattern.compile("\\{[^\\{\\}]*\\}")

    private const val BRACKET_LEFT = "{"
    private const val BRACKET_RIGHT = "}"
    private const val STEP_POINTER = "#"

    private const val PARENT = "#parent"
    private const val RESULT = "result"

    private val stepsValues = mutableMapOf<String, MutableMap<String, Any?>>()

    fun registerResult(name: String, result: JsonResponse) {
        val stepMap: MutableMap<String, Any?> = stepsValues[name] ?: return

        stepMap[RESULT] = result.response
    }

    fun registerStep(name: String, source: SourceMap) {
        stepsValues[name] = source.toMutableMap()
    }

    fun getValue(name: String, pointer: Any?): Any? {
        return when {
            pointer == null -> null
            pointer !is String -> pointer
            !containsVariable(pointer) -> pointer
            containsStepPointer(pointer) -> {
                val stepPointer = extractStepPointer(pointer) ?: return null

                val stepName = if (stepPointer == PARENT) name else extractStepName(stepPointer)
                val pathValue = removeBrackets(pointer).replace("$stepPointer.", "")
                val step = stepsValues[stepName] ?: return null

                getValueByPointer(pathValue, step)
            }
            else -> {
                getValueByPointer(pointer, stepsValues[name])
            }
        }
    }

    private fun extractStepName(stepPointer: String): String = stepPointer.replace(STEP_POINTER, "")

    private fun extractStepPointer(pointer: String): String? = getPrefix(removeBrackets(pointer))

    private fun getValueByPointer(pointer: String, target: Any?): Any? {
        if (target == null) return null
        return getValueByPointer(removeBrackets(pointer).safeSplit("\\."), 0, target)
    }

    private fun getValueByPointer(pointer: List<String>?, position: Int, result: Any?): Any? {
        if (result == null) return null
        if (pointer == null || position >= pointer.size) return result


        val key = pointer[position]
        if (result is Map<*, *>) {
            return getValueByPointer(pointer, position + 1, result[key])
        }

        return if (result is List<*>) {
            if (key.isNumber()) {
                val numKey = key.toIntOrNull() ?: 0
                var nextPosition = position
                if (numKey >= result.size) null else {
                    getValueByPointer(pointer, ++nextPosition, result[key.toInt()])
                }
            } else {
                val listOfResults = mutableListOf<Any?>(result.size)
                result.forEach {
                    val valueByPattern = getValueByPointer(pointer, position, it)
                    if (valueByPattern != null) listOfResults.add(valueByPattern)
                }
                if (listOfResults.isEmpty()) null else listOfResults
            }
        } else result
    }

    private fun containsVariable(pointer: String?): Boolean {
        if (pointer == null || !pointer.contains(BRACKET_LEFT)) return false

        val matcher = variablePattern.matcher(pointer)
        return matcher.find()
    }

    private fun containsStepPointer(pointer: String?): Boolean {
        if (pointer == null) return false

        return pointer.indexOf(STEP_POINTER) == 1
    }

    private fun removeBrackets(text: String): String {
        return if (text.startsWith(BRACKET_LEFT) && text.endsWith(BRACKET_RIGHT)) {
            text.substring(1, text.length - 1)
        } else {
            text
        }
    }

    private fun getPrefix(value: String): String? {
        val suffixIdx = value.indexOf(".")
        return if (suffixIdx < 0) null else value.substring(0, suffixIdx)
    }
}
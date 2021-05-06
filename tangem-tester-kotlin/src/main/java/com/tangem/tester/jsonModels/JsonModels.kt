package com.tangem.tester.jsonModels

import com.squareup.moshi.JsonClass
import com.tangem.json.JsonRpcIn


/**
[REDACTED_AUTHOR]
 */
@JsonClass(generateAdapter = true)
data class TestEnvironment(
    val description: String,
    val iterations: Int,
    val minimalFirmware: String,
    val platform: PlatformType,
)

enum class PlatformType {
    IOS, ANDROID, ANY
}

@JsonClass(generateAdapter = true)
class StepModel(
    val name: String = "UNKNOWN",
    val actionType: String = "UNKNOWN",
    val iterations: Int = 0,
    val method: String = "UNKNOWN",
    val parameters: MutableMap<String, Any?> = mutableMapOf(),
    val expectedResult: Map<String, Any?> = mapOf(),
    val asserts: List<AssertModel> = listOf(),
    val robotActions: List<RobotActionModel> = listOf(),
) {
    val rawParameters : Map<String, Any?> = parameters.toMap()

    fun toJsonRpcIn(): JsonRpcIn = JsonRpcIn(method, parameters)
}

@JsonClass(generateAdapter = true)
data class AssertModel(
    val type: String,
    val fields: List<String>
)

@JsonClass(generateAdapter = true)
data class RobotActionModel(
    val type: String,
    val parameters: Map<String, Any>? = null
)







package com.tangem.tester.jsonModels

import com.tangem.Config
import com.tangem.commands.common.card.FirmwareVersion
import com.tangem.commands.personalization.entities.CardConfig

/**
[REDACTED_AUTHOR]
 */
data class TestModel(
    val setupModel: SetupModel,
    val stepModelList: List<StepModel>
)

data class SetupModel(
    val description: String,
    val iterations: Int,
    val minimalFirmware: FirmwareVersion,
    val platform: PlatformType,
    val cardConfig: CardConfig,
    val sdkConfig: Config,
    val sessionConfig: SessionConfigModel
)

enum class PlatformType {
    IOS, ANDROID, ANY
}

data class SessionConfigModel(
    val any: Any
)

data class StepModel(
    val actionType: String,
    val action: String,
    val name: String,
    val parameters: Map<String, Any?> = mapOf(),
    val expectedResult: Map<String, Any?>,
    val asserts: List<AssertModel> = listOf(),
    val robotActions: List<RobotActionModel> = listOf(),
    val iterations: Int = 1
)

data class AssertModel(
    val action: String,
    val fields: List<String>
)

data class RobotActionModel(
    val type: String,
    val parameters: Map<String, Any>? = null
)







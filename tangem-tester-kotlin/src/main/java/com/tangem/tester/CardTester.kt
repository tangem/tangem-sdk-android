package com.tangem.tester

import com.tangem.tester.common.ExecutableFactory
import com.tangem.tester.common.ExecutableResult
import com.tangem.tester.common.JsonConverter
import com.tangem.tester.common.TangemSdkFactory
import com.tangem.tester.jsonModels.TestModel

/**
[REDACTED_AUTHOR]
 */
class CardTester(
    private val sdkFactory: TangemSdkFactory,
    private val actionFactory: ExecutableFactory,
    private val jsonConverter: JsonConverter
) {

    fun executeTest(json: String) {
        val testModel = jsonConverter.fromJson(json, TestModel::class.java)
            ?: throw NullPointerException()

        sdkFactory.create(testModel.setupModel.sdkConfig)

        testModel.stepModelList.forEach {
            val step = actionFactory.getStep(it.name)
                ?: throw UnsupportedOperationException("Step action: ${it.action} is not registered")

            step.setup(sdkFactory, actionFactory, it)
            for (successLaunchCount in 0 until step.getIterationCount())
                step.run { stepResult ->
                    when (stepResult) {
                        is ExecutableResult.Failure -> {
                        }
                        is ExecutableResult.Success -> {
                        }
                    }
                }
        }
    }
}
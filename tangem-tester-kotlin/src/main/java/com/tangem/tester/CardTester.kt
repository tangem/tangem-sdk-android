package com.tangem.tester

import com.tangem.CardSession
import com.tangem.Config
import com.tangem.commands.common.jsonConverter.MoshiJsonConverter
import com.tangem.common.extensions.guard
import com.tangem.json.JsonAdaptersFactory
import com.tangem.tester.common.*
import com.tangem.tester.executable.AssertsFactory
import com.tangem.tester.executable.steps.TestStep
import com.tangem.tester.jsonModels.StepModel
import com.tangem.tester.jsonModels.TestEnvironment
import com.tangem.tester.variables.VariableService
import java.util.*

/**
[REDACTED_AUTHOR]
 */
class CardTester(
    private val sdkFactory: TangemSdkFactory,
    private val assertsFactory: AssertsFactory,
    private val jsonRunnableFactory: JsonAdaptersFactory = JsonAdaptersFactory()
) {
    var onTestComplete: OnComplete? = null

    private var testQueue: Queue<SourceMap> = LinkedList()
    private var stepQueue: Queue<StepModel> = LinkedList()
    private lateinit var testEnvironment: TestEnvironment

    private val jsonConverter = MoshiJsonConverter.tangemSdkJsonConverter()

    fun runFromJson(json: String) {
        val jsonMap: SourceMap = jsonConverter.mapFromJson(json)

        testEnvironment = createTestEnvironment(jsonMap).guard {
            onTestComplete?.invoke(TestResult.Failure(TestError.EnvironmentInitError()))
            return
        }

        testQueue = createTestQueue(jsonMap)
        if (testQueue.isEmpty()) {
            onTestComplete?.invoke(TestResult.Failure(TestError.TestIsEmptyError()))
        } else {
            runTest(testQueue.poll())
        }
    }

    private fun runTest(sourceMap: SourceMap) {
        stepQueue = createStepQueue(sourceMap)
        if (stepQueue.isEmpty()) {
            onTestComplete?.invoke(TestResult.Failure(TestError.StepsIsEmptyError()))
        } else {
            createSession { session -> runStep(session, stepQueue.poll()) }
        }
    }

    private fun runStep(session: CardSession, stepModel: StepModel) {
        TestStep(stepModel).apply {
            init(session, jsonRunnableFactory, assertsFactory)
            run { stepResult -> onStepSequenceComplete(session, stepResult) }
        }
    }

    private val onStepSequenceComplete: OnStepSequenceComplete = { session, result ->
        when (result) {
            is TestResult.Success -> {
                if (stepQueue.isEmpty()) {
                    session.stop()
                    onTestSequenceComplete(null)
                } else {
                    runStep(session, stepQueue.poll())
                }
            }
            is TestResult.Failure -> {
                session.stopWithError(result.error.toTangemError())
                onTestSequenceComplete(result.error)
            }
        }
    }

    private val onTestSequenceComplete: OnTestSequenceComplete = { error ->
        if (error == null) {
            if (testQueue.isEmpty()) {
                onTestComplete?.invoke(TestResult.Success())
            } else {
                runTest(testQueue.poll())
            }
        } else {
            onTestComplete?.invoke(TestResult.Failure(error))
        }
    }

    private fun createSession(callback: (session: CardSession) -> Unit) {
        sdkFactory.create(createSdkConfig(testEnvironment))
            .startSession { session, error ->
                if (error == null) {
                    callback(session)
                } else {
                    onTestComplete?.invoke(TestResult.Failure(TestError.SessionSdkInitError(error)))
                }
            }
    }

    private fun createTestQueue(jsonSourceMap: SourceMap): Queue<SourceMap> = LinkedList<SourceMap>().apply {
        testEnvironment.iterations.foreach { offer(jsonSourceMap) }
    }

    private fun createStepQueue(jsonMap: SourceMap): Queue<StepModel> = LinkedList<StepModel>().apply {
        (jsonMap["steps"] as? Collection<SourceMap>)?.forEach { modelMap ->
            jsonConverter.fromJson<StepModel>(jsonConverter.toJson(modelMap))?.let {
                VariableService.registerStep(it.name, modelMap)
                offer(it)
            }
        }
    }

    private fun createSdkConfig(setupModel: TestEnvironment): Config {
        return Config()
    }


    private fun createTestEnvironment(jsonMap: Map<*, *>): TestEnvironment? {
        val setupJson = jsonConverter.toJson(jsonMap["setup"]!!)
        return jsonConverter.fromJson<TestEnvironment>(setupJson)
    }
}
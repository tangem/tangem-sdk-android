package com.tangem.tester.executable.steps

import com.tangem.CardSession
import com.tangem.common.CompletionResult
import com.tangem.common.extensions.guard
import com.tangem.json.JsonAdaptersFactory
import com.tangem.json.JsonResponse
import com.tangem.tester.*
import com.tangem.tester.common.*
import com.tangem.tester.executable.AssertHolder
import com.tangem.tester.executable.Executable
import com.tangem.tester.executable.asserts.AssertsLauncher
import com.tangem.tester.jsonModels.AssertModel
import com.tangem.tester.jsonModels.StepModel
import com.tangem.tester.variables.VariableService
import java.util.*

/**
[REDACTED_AUTHOR]
 */
interface Step : Executable {
    fun getIterationCount(): Int
    fun getActionType(): String = "NFC_SESSION_RUNNABLE"
    fun init(runnableFactory: JsonAdaptersFactory, assertHolder: AssertHolder, model: StepModel)
    fun run(session: CardSession, callback: (TestResult) -> Unit)
}

open class TestStep(
    private val stepName: String
) : Step {

    protected lateinit var runnableFactory: JsonAdaptersFactory
    protected lateinit var assertsHolder: AssertHolder
    protected lateinit var model: StepModel

    override fun getName(): String = stepName

    override fun getIterationCount(): Int = model.iterations

    override fun init(runnableFactory: JsonAdaptersFactory, assertHolder: AssertHolder, model: StepModel) {
        this.runnableFactory = runnableFactory
        this.assertsHolder = assertHolder
        this.model = model
    }

    override fun run(session: CardSession, callback: (TestResult) -> Unit) {
        val jsonRpc = runnableFactory.jsonConverter.toMap(model.toJsonRpcIn())
        val jsonRunnableAdapter = runnableFactory.createFrom(jsonRpc).guard {
            callback(TestResult.Failure(TestError.MissingJsonAdapterError(model.method)))
            return
        }

        jsonRunnableAdapter.run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    val jsonResponse = (result.data as? JsonResponse).guard {
                        callback(TestResult.Failure(ExecutableError.UnexpectedResponseError(result.data)))
                        return@run
                    }
                    checkForExpectedResult(jsonResponse)?.let {
                        callback(TestResult.Failure(it))
                        return@run
                    }

                    VariableService.registerResult(model.name, jsonResponse)
                    executeAsserts(callback)
                }
                is CompletionResult.Failure -> callback(TestResult.Failure(result.error.toFrameworkError()))
            }
        }
    }

    override fun fetchVariables(name: String): ExecutableError? {
        model.parameters.clear()
        model.rawParameters.forEach {
            model.parameters[it.key] = VariableService.getValue(model.name, it.value)
        }
        return null
    }

    protected open fun checkForExpectedResult(result: JsonResponse): ExecutableError? {
        val errorsList = model.expectedResult.mapNotNull { expected ->
            val value = result.response[expected.key]
            if (expected.value != null && value == null) {
                "Result doesn't contains value for: ${expected.key}"
            } else {
                if (expected.value != value) {
                    "Result value doesn't match. Expected value: ${expected.value}, result value: $value"
                } else {
                    null
                }
            }
        }
        return if (errorsList.isEmpty()) null else ExecutableError.ExpectedResultError(errorsList)
    }

    private fun executeAsserts(callback: (TestResult) -> Unit) {
        if (model.asserts.isEmpty()) {
            callback(TestResult.Success())
            return
        }

        val assertsQueue: Queue<AssertModel> = LinkedList()
        model.asserts.forEach { assertsQueue.offer(it) }
        AssertsLauncher(model.name, assertsQueue, assertsHolder).run(callback)
    }
}
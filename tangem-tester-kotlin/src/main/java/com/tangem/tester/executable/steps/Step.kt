package com.tangem.tester.executable.steps

import com.tangem.CardSession
import com.tangem.common.CompletionResult
import com.tangem.common.extensions.guard
import com.tangem.json.JsonAdaptersFactory
import com.tangem.json.JsonResponse
import com.tangem.json.JsonRunnableAdapter
import com.tangem.tester.*
import com.tangem.tester.common.*
import com.tangem.tester.executable.AssertsFactory
import com.tangem.tester.executable.Executable
import com.tangem.tester.executable.asserts.Assert
import com.tangem.tester.executable.asserts.AssertsLauncher
import com.tangem.tester.jsonModels.StepModel
import com.tangem.tester.variables.VariableService
import java.util.*

/**
[REDACTED_AUTHOR]
 */
interface Step : Executable {
    fun getIterationCount(): Int
    fun getActionType(): String = "NFC_SESSION_RUNNABLE"
    fun init(session: CardSession, runnableFactory: JsonAdaptersFactory, assertsFactory: AssertsFactory)
}

open class TestStep(
    private val model: StepModel
) : Step {

    protected lateinit var session: CardSession
    protected lateinit var runnableFactory: JsonAdaptersFactory
    protected lateinit var assertsFactory: AssertsFactory

    override fun getMethod(): String = model.method

    override fun getIterationCount(): Int = model.iterations

    override fun init(session: CardSession, runnableFactory: JsonAdaptersFactory, assertsFactory: AssertsFactory) {
        this.session = session
        this.runnableFactory = runnableFactory
        this.assertsFactory = assertsFactory
    }

    override fun run(callback: OnComplete) {
        if (isInitialized()) {
            callback(TestResult.Failure(ExecutableError.ExecutableNotInitialized(model.name)))
            return
        }

        fetchVariables()
        val jsonRpc = runnableFactory.jsonConverter.toMap(model.toJsonRpcIn())
        val jsonRunnableAdapter = runnableFactory.createFrom(jsonRpc).guard {
            callback(TestResult.Failure(ExecutableError.MissingJsonAdapterError(model.method)))
            return
        }

        runInternal(jsonRunnableAdapter, callback)
    }

    protected open fun runInternal(jsonRunnableAdapter: JsonRunnableAdapter<*>, callback: OnComplete) {
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

    private fun isInitialized(): Boolean = !this::session.isInitialized || !this::runnableFactory.isInitialized
        || !this::assertsFactory.isInitialized

    private fun fetchVariables() {
        model.parameters.clear()
        model.rawParameters.forEach { model.parameters[it.key] = VariableService.getValue(model.name, it.value) }
    }

    private fun executeAsserts(callback: OnComplete) {
        if (model.asserts.isEmpty()) {
            callback(TestResult.Success())
            return
        }

        val assertsQueue: Queue<Assert> = LinkedList()
        model.asserts.forEach {
            val assert = assertsFactory.getAssert(it.type).guard {
                callback(TestResult.Failure(ExecutableError.ExecutableNotFoundError(it.type)))
                return
            }
            assert.init(model.name, it.fields)
            assertsQueue.offer(assert)
        }
        AssertsLauncher(assertsQueue).run(callback)
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
}
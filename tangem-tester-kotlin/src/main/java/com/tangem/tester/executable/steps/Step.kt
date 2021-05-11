package com.tangem.tester.executable.steps

import com.tangem.CardSession
import com.tangem.commands.CommandResponse
import com.tangem.common.CompletionResult
import com.tangem.json.JsonAdaptersFactory
import com.tangem.tester.*
import com.tangem.tester.common.*
import com.tangem.tester.executable.AssertHolder
import com.tangem.tester.executable.Executable
import com.tangem.tester.jsonModels.StepModel
import com.tangem.tester.variables.VariableService

/**
[REDACTED_AUTHOR]
 */
interface Step<T : CommandResponse> : Executable {
    fun getIterationCount(): Int
    fun getActionType(): String = "NFC_SESSION_RUNNABLE"
    fun setup(runnableFactory: JsonAdaptersFactory, assertHolder: AssertHolder, model: StepModel): Step<T>
}

abstract class BaseStep<T : CommandResponse>(
    private val stepName: String
) : Step<T> {

    protected lateinit var runnableFactory: JsonAdaptersFactory
    protected lateinit var assertHolder: AssertHolder
    protected lateinit var model: StepModel

    override fun getName(): String = stepName

    override fun getIterationCount(): Int = model.iterations

    override fun setup(runnableFactory: JsonAdaptersFactory, assertHolder: AssertHolder, model: StepModel): Step<T> {
        this.runnableFactory = runnableFactory
        this.assertHolder = assertHolder
        this.model = model
        return this
    }

    override fun run(session: CardSession, callback: (StepResult) -> Unit) {
        fetchVariables(model.name)?.let {
            callback(StepResult.Failure(it))
            return
        }

        val jsonRpc = runnableFactory.jsonConverter.toMap(model.toJsonRpcIn())
        runnableFactory.createFrom(jsonRpc)?.run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    VariableService.registerResult(model.name, result.data)
                    checkForExpectedResult(result.data as T)?.let {
                        callback(StepResult.Failure(it))
                        return@run
                    }
                    executeAsserts(callback)
                }
                is CompletionResult.Failure -> callback(StepResult.Failure(result.error.toFrameworkError()))
            }
        }
    }

    protected fun executeAsserts(callback: (StepResult) -> Unit) {
        if (model.asserts.isEmpty()) {
            callback(StepResult.Success(model.name))
            return
        }

        model.asserts.mapNotNull { assertHolder.getAssert(it.type).apply { this?.setup(it) } }.map {
            it.fetchVariables(model.name)?.let { error ->
                callback(StepResult.Failure(error))
                return
            }
        }
        callback(StepResult.Success(model.name))
    }

    protected fun checkResultFields(vararg pair: CheckPair): List<String> {
        return pair.mapNotNull {
            if (model.expectedResult[it.fieldName] == it.expectedValue) null
            else "Field ${it.fieldName} doesn't match with result value"
        }
    }

    protected abstract fun checkForExpectedResult(result: T): ExecutableError?

    protected data class CheckPair(val fieldName: String, val expectedValue: Any?)
}
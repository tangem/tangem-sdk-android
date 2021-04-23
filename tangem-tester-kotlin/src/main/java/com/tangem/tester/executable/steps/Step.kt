package com.tangem.tester.executable.steps

import com.tangem.CardSessionRunnable
import com.tangem.TangemError
import com.tangem.commands.CommandResponse
import com.tangem.common.CompletionResult
import com.tangem.tester.*
import com.tangem.tester.common.*
import com.tangem.tester.jsonModels.StepModel
import com.tangem.tester.services.VariableService

/**
[REDACTED_AUTHOR]
 */
interface Step<T : CommandResponse> : Executable {
    fun getIterationCount(): Int
    fun getActionType(): String = "NFC_SESSION_RUNNABLE"
    fun setup(sdkHolder: TangemSdkHolder, assertHolder: AssertHolder, model: StepModel): Step<T>
}

abstract class BaseStep<T : CommandResponse>(
    private val stepName: String
) : Step<T> {

    protected lateinit var sdkHolder: TangemSdkHolder
    protected lateinit var actionHolder: AssertHolder
    protected lateinit var model: StepModel

    override fun getName(): String = stepName

    override fun getIterationCount(): Int = model.iterations

    override fun setup(sdkHolder: TangemSdkHolder, assertHolder: AssertHolder, model: StepModel): Step<T> {
        this.sdkHolder = sdkHolder
        this.actionHolder = assertHolder
        this.model = model
        return this
    }

    override fun run(callback: ExecutableCallback) {
        fetchVariables(model.name)?.let {
            callback(ExecutableResult.Failure(it))
            return
        }

        sdkHolder.getSdk().startSessionWithRunnable(getRunnable()) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    VariableService.registerResult(model.name, result.data)
                    checkForExpectedResult(result.data)?.let {
                        callback(ExecutableResult.Failure(it))
                        return@startSessionWithRunnable
                    }
                    executeAsserts(callback)
                }
                is CompletionResult.Failure -> callback(result.error.toTestFailure())
            }
        }
    }

    protected fun executeAsserts(callback: ExecutableCallback) {
        if (model.asserts.isEmpty()) {
            callback(ExecutableResult.Success())
            return
        }

        model.asserts.mapNotNull { actionHolder.getAssert(it.action).apply { this?.setup(it) } }.map {
            it.fetchVariables(model.name)?.let { error ->
                callback(ExecutableResult.Failure(error))
                return
            }
            it.run { result ->
                when (result) {
                    is ExecutableResult.Failure -> callback(ExecutableResult.Failure(result.error))
                    is ExecutableResult.Success -> {
                    }
                }
            }
        }
        callback(ExecutableResult.Success())
    }

    protected fun checkResultFields(vararg pair: CheckPair): List<String> {
        return pair.mapNotNull {
            if (model.expectedResult[it.fieldName] == it.expectedValue) null
            else "Field ${it.fieldName} doesn't match with result value"
        }
    }

    protected abstract fun getRunnable(): CardSessionRunnable<T>
    protected abstract fun checkForExpectedResult(result: T): ExecutableError.ExpectedResultError?

    protected data class CheckPair(val fieldName: String, val expectedValue: Any?)
}


fun TangemError.toTestFailure(): ExecutableResult.Failure {
    return ExecutableResult.Failure(ExecutableError.SdkError(this))
}
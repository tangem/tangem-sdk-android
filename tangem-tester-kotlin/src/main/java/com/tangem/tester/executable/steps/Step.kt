package com.tangem.tester.executable.steps

import com.tangem.CardSession
import com.tangem.CardSessionRunnable
import com.tangem.commands.common.jsonRpc.JSONRPCConverter
import com.tangem.commands.common.jsonRpc.JSONRPCResponse
import com.tangem.common.CompletionResult
import com.tangem.common.extensions.guard
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
    fun init(session: CardSession, jsonRpcConverter: JSONRPCConverter, assertsFactory: AssertsFactory)
}

open class TestStep(
    private val model: StepModel
) : Step {

    protected lateinit var session: CardSession
    protected lateinit var jsonRpcConverter: JSONRPCConverter
    protected lateinit var assertsFactory: AssertsFactory

    override fun getMethod(): String = model.method

    override fun getIterationCount(): Int = model.iterations

    override fun init(session: CardSession, jsonRpcConverter: JSONRPCConverter, assertsFactory: AssertsFactory) {
        this.session = session
        this.jsonRpcConverter = jsonRpcConverter
        this.assertsFactory = assertsFactory
    }

    override fun run(callback: OnComplete) {
        if (isInitialized()) {
            callback(TestResult.Failure(ExecutableError.ExecutableNotInitialized(model.name)))
            return
        }

        fetchVariables()
        val runnable = jsonRpcConverter.convert(model.toJSONRPCRequest()).guard {
            callback(TestResult.Failure(ExecutableError.MissingJsonAdapterError(model.method)))
            return
        }

        runInternal(runnable, callback)
    }

    protected open fun runInternal(runnable: CardSessionRunnable<*>, callback: OnComplete) {
        runnable.run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    val jsonResponse = (result.data as? JSONRPCResponse).guard {
                        callback(TestResult.Failure(ExecutableError.UnexpectedResponseError(result.data)))
                        return@run
                    }

                    VariableService.registerResult(model.name, jsonResponse)
                    executeAsserts(callback)
                }
                is CompletionResult.Failure -> callback(TestResult.Failure(result.error.toFrameworkError()))
            }
        }
    }

    private fun isInitialized(): Boolean = !this::session.isInitialized || !this::jsonRpcConverter.isInitialized
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
}
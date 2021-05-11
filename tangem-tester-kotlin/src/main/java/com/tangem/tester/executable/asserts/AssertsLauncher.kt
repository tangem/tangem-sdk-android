package com.tangem.tester.executable.asserts

import com.tangem.common.extensions.guard
import com.tangem.tester.common.TestError
import com.tangem.tester.common.TestResult
import com.tangem.tester.executable.AssertHolder
import com.tangem.tester.jsonModels.AssertModel
import java.util.*

/**
[REDACTED_AUTHOR]
 */
class AssertsLauncher(
    private val parentStepName: String,
    private val assertsQueue: Queue<AssertModel>,
    private val assertHolder: AssertHolder,
) {
    private lateinit var onAssertSequenceComplete: (TestResult) -> Unit

    private val onAssertComplete: (TestResult) -> Unit = { result ->
        when (result) {
            is TestResult.Success -> {
                if (assertsQueue.isEmpty()) {
                    onAssertSequenceComplete(TestResult.Success())
                } else {
                    executeAssert(assertsQueue.poll())
                }
            }
            is TestResult.Failure -> onAssertSequenceComplete(result)
        }
    }

    fun run(callback: (TestResult) -> Unit) {
        if (assertsQueue.isEmpty()) {
            callback(TestResult.Success())
            return
        }

        onAssertSequenceComplete = callback
        executeAssert(assertsQueue.poll())
    }

    private fun executeAssert(assertModel: AssertModel) {
        val assert = assertHolder.getAssert(assertModel.type).guard {
            onAssertComplete(TestResult.Failure(TestError.ExecutableNotFoundError(assertModel.type)))
            return
        }

        assert.init(assertModel)
        assert.fetchVariables(parentStepName)?.let { error ->
            onAssertComplete(TestResult.Failure(error))
            return@let
        }

        assert.run { result ->
            when (result) {
                is TestResult.Success -> onAssertComplete(TestResult.Success(result.name))
                is TestResult.Failure -> onAssertComplete(result)
            }
        }
    }
}
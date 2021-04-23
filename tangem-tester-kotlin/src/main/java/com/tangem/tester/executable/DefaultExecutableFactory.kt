package com.tangem.tester.executable

import com.tangem.tester.common.ExecutableFactory
import com.tangem.tester.executable.asserts.Assert
import com.tangem.tester.executable.asserts.EqualsAssert
import com.tangem.tester.executable.steps.ScanStep
import com.tangem.tester.executable.steps.SignStep
import com.tangem.tester.executable.steps.Step

/**
[REDACTED_AUTHOR]
 */
class DefaultExecutableFactory : ExecutableFactory {

    private val testSteps = mutableMapOf<String, Step<*>>()
    private val asserts = mutableMapOf<String, Assert>()

    fun registerAssert(assert: Assert) {
        asserts[assert.getName()] = assert
    }

    fun registerStep(runnable: Step<*>) {
        testSteps[runnable.getName()] = runnable
    }

    override fun getStep(name: String): Step<*>? {
        return testSteps[name]
    }

    override fun getAssert(name: String): Assert? {
        return asserts[name]
    }

    companion object {
        fun init(): ExecutableFactory {
            return DefaultExecutableFactory().apply {
                registerStep(SignStep())
                registerStep(ScanStep())
                registerAssert(EqualsAssert())
            }
        }
    }
}
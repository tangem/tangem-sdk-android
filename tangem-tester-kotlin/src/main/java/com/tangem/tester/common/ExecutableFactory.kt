package com.tangem.tester.common

import com.tangem.tester.executable.asserts.Assert
import com.tangem.tester.executable.steps.Step

/**
[REDACTED_AUTHOR]
 */
interface ExecutableFactory : StepHolder, AssertHolder

interface AssertHolder {
    fun getAssert(name: String): Assert?
}

interface StepHolder {
    fun getStep(name: String): Step<*>?
}
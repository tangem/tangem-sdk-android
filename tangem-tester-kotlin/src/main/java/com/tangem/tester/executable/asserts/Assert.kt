package com.tangem.tester.executable.asserts

import com.tangem.tester.common.TestResult
import com.tangem.tester.executable.Executable
import com.tangem.tester.jsonModels.AssertModel

/**
[REDACTED_AUTHOR]
 */
interface Assert : Executable {
    fun init(model: AssertModel): Assert
    fun run(callback: (TestResult) -> Unit)
}

abstract class BaseAssert(
    private val assertName: String
) : Assert {
    protected lateinit var model: AssertModel

    override fun getName(): String = assertName

    override fun init(model: AssertModel): Assert {
        this.model = model
        return this
    }
}

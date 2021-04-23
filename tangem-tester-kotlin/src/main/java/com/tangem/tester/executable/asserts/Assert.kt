package com.tangem.tester.executable.asserts

import com.tangem.tester.common.Executable
import com.tangem.tester.jsonModels.AssertModel

/**
[REDACTED_AUTHOR]
 */
interface Assert : Executable {
    fun setup(model: AssertModel): Assert
}

abstract class BaseAssert(
    private val assertName: String
) : Assert {
    protected lateinit var model: AssertModel

    override fun getName(): String = assertName

    override fun setup(model: AssertModel): Assert {
        this.model = model
        return this
    }
}

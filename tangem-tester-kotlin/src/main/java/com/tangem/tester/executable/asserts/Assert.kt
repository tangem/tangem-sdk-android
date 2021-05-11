package com.tangem.tester.executable.asserts

import com.tangem.tester.common.ExecutableError
import com.tangem.tester.common.OnComplete
import com.tangem.tester.common.TestResult
import com.tangem.tester.executable.Executable
import com.tangem.tester.variables.VariableService

/**
[REDACTED_AUTHOR]
 */
interface Assert : Executable {
    fun init(parentName: String, fields: List<String>)
}

abstract class BaseAssert(
    private val type: String
) : Assert {

    protected lateinit var parentName: String
    protected lateinit var fields: List<String>

    override fun getMethod(): String = type

    override fun init(parentName: String, fields: List<String>) {
        this.parentName = parentName
        this.fields = fields
    }

    override fun run(callback: OnComplete) {
        if (isInitialized()) {
            callback(TestResult.Failure(ExecutableError.ExecutableNotInitialized(getMethod())))
            return
        }
        assert(callback)
    }

    private fun isInitialized(): Boolean = !this::parentName.isInitialized || !this::fields.isInitialized

    abstract fun assert(callback: OnComplete)

    protected fun getFieldValue(pointer: String): Any? = VariableService.getValue(parentName, pointer)
}

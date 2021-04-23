package com.tangem.tester.executable.asserts

import com.tangem.tester.common.ExecutableCallback
import com.tangem.tester.common.ExecutableError
import com.tangem.tester.common.ExecutableResult
import com.tangem.tester.services.VariableService

class EqualsAssert : BaseAssert("EQUALS") {

    private var firstValue: Any? = null
    private var secondValue: Any? = null

    override fun fetchVariables(name: String): ExecutableError.InitError? {
        firstValue = VariableService.getValue(name, model.fields[0])
        secondValue = VariableService.getValue(name, model.fields[1])
        return null
    }

    override fun run(callback: ExecutableCallback) {
        if (firstValue == secondValue) {
            callback(ExecutableResult.Success())
        } else {
            val error = ExecutableError.AssertError("Fields doesn't match. f1: $firstValue, f2: $secondValue")
            callback(ExecutableResult.Failure(error))
        }
    }
}
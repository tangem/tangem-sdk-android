package com.tangem.tester.executable.asserts

import com.tangem.tester.common.AssertError
import com.tangem.tester.common.ExecutableError
import com.tangem.tester.common.TestResult
import com.tangem.tester.variables.VariableService

class EqualsAssert : BaseAssert("EQUALS") {

    private var firstValue: Any? = null
    private var secondValue: Any? = null

    override fun fetchVariables(name: String): ExecutableError? {
        firstValue = VariableService.getValue(name, model.fields[0])
        secondValue = VariableService.getValue(name, model.fields[1])
        return null
    }

    override fun run(callback: (TestResult) -> Unit) {
        if (firstValue == secondValue) {
            callback(TestResult.Success(model.type))
        } else {
            val error = AssertError.EqualsError(firstValue, secondValue)
            callback(TestResult.Failure(error))
        }
    }
}
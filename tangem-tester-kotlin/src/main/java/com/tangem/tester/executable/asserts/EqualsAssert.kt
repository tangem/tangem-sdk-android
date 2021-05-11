package com.tangem.tester.executable.asserts

import com.tangem.CardSession
import com.tangem.tester.common.ExecutableError
import com.tangem.tester.common.StepResult
import com.tangem.tester.variables.VariableService

class EqualsAssert : BaseAssert("EQUALS") {

    private var firstValue: Any? = null
    private var secondValue: Any? = null

    override fun fetchVariables(name: String): ExecutableError? {
        firstValue = VariableService.getValue(name, model.fields[0])
        secondValue = VariableService.getValue(name, model.fields[1])
        return null
    }

    override fun run(session: CardSession, callback: (StepResult) -> Unit) {
        if (firstValue == secondValue) {
            callback(StepResult.Success(model.type))
        } else {
            val error = ExecutableError.AssertError("Fields doesn't match. f1: $firstValue, f2: $secondValue")
            callback(StepResult.Failure(error))
        }
    }
}
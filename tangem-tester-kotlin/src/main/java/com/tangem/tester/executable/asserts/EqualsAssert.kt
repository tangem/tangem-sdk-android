package com.tangem.tester.executable.asserts

import com.tangem.tester.common.AssertError
import com.tangem.tester.common.OnComplete
import com.tangem.tester.common.TestResult

class EqualsAssert : BaseAssert("EQUALS") {

    override fun assert(callback: OnComplete) {
        val firstValue = getFieldValue(fields[0])
        val secondValue = getFieldValue(fields[1])

        if (firstValue == secondValue) {
            callback(TestResult.Success(getMethod()))
        } else {
            val error = AssertError.EqualsError(firstValue, secondValue)
            callback(TestResult.Failure(error))
        }
    }
}
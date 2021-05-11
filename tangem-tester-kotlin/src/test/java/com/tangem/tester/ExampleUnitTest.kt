package com.tangem.tester

import com.tangem.tester.variables.VariableService
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        VariableService.getValue("someName", "{#resultName.params.value}")
    }
}
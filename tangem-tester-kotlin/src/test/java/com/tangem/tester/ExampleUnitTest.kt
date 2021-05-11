package com.tangem.tester

import com.tangem.commands.SignResponse
import com.tangem.tester.variables.VariableService
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNull
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 */
class ExampleUnitTest {
    @Test
    fun extractVariables() {
        val name = "signResponse"
        val cardId = "CB034555"
        VariableService.registerResult(name, SignResponse(cardId, listOf(), 5, 2))

        // test step pointer
        assertNull(VariableService.getValue(name, "{#unknownPath.cardId}"))
        assertNull(VariableService.getValue(name, "{#unknownPath}"))
        assertNull(VariableService.getValue(name, "{anyVariableName}"))
        assertNull(VariableService.getValue("anyName", "{anyVariableName}"))

        assertEquals(cardId, VariableService.getValue(name, "{#parent.cardId}"))
        assertEquals(cardId, VariableService.getValue(name, "{#$name.cardId}"))
        assertEquals(cardId, VariableService.getValue(name, "{cardId}"))
        assertEquals(5.toDouble(), VariableService.getValue(name, "{#parent.walletRemainingSignatures}"))
        assertEquals(2.toDouble(), VariableService.getValue(name, "{#parent.walletSignedHashes}"))
    }
}
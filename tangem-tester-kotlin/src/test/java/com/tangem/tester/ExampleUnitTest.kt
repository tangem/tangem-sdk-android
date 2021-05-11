package com.tangem.tester

import com.tangem.commands.SignResponse
import com.tangem.commands.common.jsonConverter.MoshiJsonConverter
import com.tangem.json.JsonResponse
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
        val converter = MoshiJsonConverter.tangemSdkJsonConverter()
        val resultMap = converter.toMap(SignResponse(cardId, listOf(), 5, 2))
        VariableService.registerStep(name, mutableMapOf())
        VariableService.registerResult(name, JsonResponse(resultMap))

        // test step pointer
        assertNull(VariableService.getValue(name, "{#unknownPath.cardId}"))
        assertNull(VariableService.getValue(name, "{#unknownPath}"))
        assertNull(VariableService.getValue(name, "{anyVariableName}"))
        assertNull(VariableService.getValue("anyName", "{anyVariableName}"))

        assertEquals(cardId, VariableService.getValue(name, "{#parent.result.cardId}"))
        assertEquals(cardId, VariableService.getValue(name, "{#$name.result.cardId}"))
        assertEquals(cardId, VariableService.getValue(name, "{result.cardId}"))
        assertEquals(5.toDouble(), VariableService.getValue(name, "{#parent.result.walletRemainingSignatures}"))
        assertEquals(2.toDouble(), VariableService.getValue(name, "{#parent.result.walletSignedHashes}"))
    }
}
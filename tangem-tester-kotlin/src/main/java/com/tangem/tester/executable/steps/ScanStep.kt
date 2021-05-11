package com.tangem.tester.executable.steps

import com.tangem.commands.common.card.Card
import com.tangem.common.extensions.toHexString
import com.tangem.tester.common.ExecutableError
import com.tangem.tester.variables.VariableService

/**
[REDACTED_AUTHOR]
 */
class ScanStep : BaseStep<Card>("SCAN_TASK") {

    companion object {
        val keyCardVerification = "cardVerification"
    }

    override fun fetchVariables(name: String): ExecutableError? {
        return try {
            val cardVerification = VariableService.getValue(name, model.rawParameters[keyCardVerification]) as Boolean
            model.parameters[keyCardVerification] = cardVerification
            null
        } catch (ex: Exception) {
            ExecutableError.FetchVariableError(ex.toString())
        }
    }

    override fun checkForExpectedResult(result: Card): ExecutableError? {
        val errorsList = checkResultFields(
            CheckPair("cardId", result.cardId),
            CheckPair("isActivated", result.isActivated),
            CheckPair("cardPublicKey", result.cardPublicKey?.toHexString()),
        )
        return if (errorsList.isEmpty()) null else ExecutableError.ExpectedResultError(errorsList)
    }
}
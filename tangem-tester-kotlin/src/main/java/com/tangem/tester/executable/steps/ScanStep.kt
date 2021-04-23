package com.tangem.tester.executable.steps

import com.tangem.CardSessionRunnable
import com.tangem.commands.common.card.Card
import com.tangem.common.extensions.toHexString
import com.tangem.tasks.ScanTask
import com.tangem.tester.common.ExecutableError
import com.tangem.tester.services.VariableService

/**
[REDACTED_AUTHOR]
 */
class ScanStep : BaseStep<Card>("SCAN_COMMAND") {

    companion object {
        val keyCardVerification = "cardVerification"
    }

    private var cardVerification: Boolean = false

    override fun fetchVariables(name: String): ExecutableError.InitError? {
        return try {
            cardVerification = VariableService.getValue(name, model.parameters[keyCardVerification]) as Boolean
            null
        } catch (ex: Exception) {
            ExecutableError.InitError(ex.toString())
        }
    }

    override fun getRunnable(): CardSessionRunnable<Card> = ScanTask(cardVerification)

    override fun checkForExpectedResult(result: Card): ExecutableError.ExpectedResultError? {
        val errorsList = checkResultFields(
            CheckPair("cardId", result.cardId),
            CheckPair("isActivated", result.isActivated),
            CheckPair("cardPublicKey", result.cardPublicKey?.toHexString()),
        )
        return if (errorsList.isEmpty()) null else ExecutableError.ExpectedResultError(errorsList)
    }
}
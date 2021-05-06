package com.tangem.tester.executable

import com.tangem.CardSession
import com.tangem.tester.common.StepResult
import com.tangem.tester.common.VariableHolder

/**
[REDACTED_AUTHOR]
 */
interface Executable : VariableHolder {
    fun getName(): String
    fun run(session: CardSession, callback: (StepResult) -> Unit)
}
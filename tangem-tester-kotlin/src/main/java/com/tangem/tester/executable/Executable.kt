package com.tangem.tester.executable

import com.tangem.tester.common.ExecutableError

/**
[REDACTED_AUTHOR]
 */
interface Executable {
    fun getName(): String
    fun fetchVariables(name: String): ExecutableError?
}
package com.tangem.tester.common

import com.tangem.TangemError

/**
[REDACTED_AUTHOR]
 */
interface Executable : VariableHolder {
    fun getName(): String
    fun run(callback: (ExecutableResult) -> Unit)
}

typealias ExecutableCallback = (ExecutableResult) -> Unit

sealed class ExecutableResult {
    class Success : ExecutableResult()
    class Failure(val error: TesterError) : ExecutableResult()
}

sealed class ExecutableError : TesterError {
    data class SdkError(val tangemError: TangemError) : ExecutableError() {
        override val errorMessage: String = "code: ${tangemError.code}, message: ${tangemError.customMessage}"
    }

    data class InitError(override val errorMessage: String) : ExecutableError()
    data class ExpectedResultError(val errorMessages: List<String>) : ExecutableError() {
        override val errorMessage: String = errorMessages.joinToString("\n")
    }

    data class AssertError(override val errorMessage: String) : ExecutableError()

}
package com.tangem.tester.common

import com.tangem.TangemError

/**
[REDACTED_AUTHOR]
 */
interface TestFrameworkError {
    val errorMessage: String
}

fun TestFrameworkError.toTangemError(): TangemError {
    return object : TangemError {
        override val code: Int = 99999
        override var customMessage: String = errorMessage
        override val messageResId: Int? = null
    }
}

fun TangemError.toFrameworkError(): TestFrameworkError {
    val code = this.code
    val customMessage = this.customMessage
    return object : TestFrameworkError {
        override val errorMessage: String = "TangemSdkError code: $code, customMessage: $customMessage"
    }
}

sealed class TestError(override val errorMessage: String) : TestFrameworkError {
    class EnvironmentInitError : TestError("Test environment initialization failed")
    class TestIsEmptyError : TestError("Test doesn't contains any data to proceed")
    class StepsIsEmptyError : TestError("Test doesn't contains any steps")
    class SessionSdkInitError(error: TangemError) : TestError("Session initialization failed. Code: ${error.code}, message: ${error.customMessage}")
    class StepNotFoundError(method: String) : TestError("Step not found for method: $method")
}

sealed class ExecutableError(override val errorMessage: String) : TestFrameworkError {
    class FetchVariableError(errorMessage: String) : ExecutableError(errorMessage)
    class ExpectedResultError(errorMessages: List<String>) : ExecutableError(errorMessages.joinToString("\n"))
    class AssertError(errorMessage: String) : ExecutableError(errorMessage)
}
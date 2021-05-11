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
}

sealed class ExecutableError(override val errorMessage: String) : TestFrameworkError {
    class ExecutableNotInitialized(name: String) : ExecutableError("Executable $name is not initialized")
    class ExecutableNotFoundError(name: String) : ExecutableError("Executable is not found for the name: $name")
    class MissingJsonAdapterError(name: String) : ExecutableError("Missing json runnable adapter for the $name")
    class FetchVariableError(paramName: Any?, path: String, exception: Throwable) : ExecutableError(
        "Fetching variable failed. Name: $paramName, path: $path, ex: ${exception.message.toString()}"
    )

    class UnexpectedResponseError(response: Any) : ExecutableError("Waiting for JsonResponse, but current is ${response::class.java.simpleName}")
    class ExpectedResultError(errorMessages: List<String>) : ExecutableError(errorMessages.joinToString("\n"))
}

sealed class AssertError(override val errorMessage: String) : TestFrameworkError {
    class EqualsError(firstValue: Any?, secondValue: Any?) : AssertError("Fields doesn't match. f1: $firstValue, f2: $secondValue")

}
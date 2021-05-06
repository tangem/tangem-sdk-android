package com.tangem.tester.common

/**
[REDACTED_AUTHOR]
 */
sealed class TestResult {
    class Success : TestResult()
    class Failure(val error: TestFrameworkError) : TestResult()
}

sealed class StepResult : TestResult() {
    class Success(val name: String) : StepResult()
    class Failure(val error: TestFrameworkError) : StepResult()
}
package com.tangem.tester.common

/**
[REDACTED_AUTHOR]
 */
sealed class TestResult {
    class Success(val name: String? = null) : TestResult()
    class Failure(val error: TestFrameworkError) : TestResult()
}
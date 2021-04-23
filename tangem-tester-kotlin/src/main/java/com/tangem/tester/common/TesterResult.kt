package com.tangem.tester.common

/**
[REDACTED_AUTHOR]
 */
sealed class TesterResult {
    class Success : TesterResult()
    class Failure(val error: TesterError) : TesterResult()
}

interface TesterError {
    val errorMessage: String
}
package com.tangem.tester.common

import com.tangem.CardSession

/**
[REDACTED_AUTHOR]
 */
typealias SourceMap = Map<String, Any?>
typealias OnComplete = (TestResult) -> Unit
typealias OnTestSequenceComplete = (TestFrameworkError?) -> Unit
typealias OnStepSequenceComplete = (CardSession, TestResult) -> Unit
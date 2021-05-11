package com.tangem.tester.executable

import com.tangem.tester.common.OnComplete

/**
[REDACTED_AUTHOR]
 */
interface Executable {
    fun getMethod(): String
    fun run(callback: OnComplete)
}
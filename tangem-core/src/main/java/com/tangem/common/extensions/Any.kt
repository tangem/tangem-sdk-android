package com.tangem.common.extensions

/**
[REDACTED_AUTHOR]
 */
inline fun<T> T?.guard(nullClause: () -> Nothing): T {
    return this ?: nullClause()
}
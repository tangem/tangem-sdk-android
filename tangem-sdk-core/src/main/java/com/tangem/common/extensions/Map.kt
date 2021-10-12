package com.tangem.common.extensions

/**
[REDACTED_AUTHOR]
 */
fun <K, V, R> Map<K, V>.mapNotNullValues(transform: (Map.Entry<K, V>) -> R?): Map<K, R> {
    val result = LinkedHashMap<K, R>()
    for (entry in this) {
        val resultValue = transform(entry)
        if (resultValue != null) result[entry.key] = resultValue
    }
    return result
}
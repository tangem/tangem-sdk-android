package com.tangem.extentions

/**
[REDACTED_AUTHOR]
 */
fun String.toSnakeCase(): String = replace("(?<=.)(?=\\p{Upper})".toRegex(), "_")

fun String.toCamelCase(): String = split('_').joinToString("", transform = String::capitalize)
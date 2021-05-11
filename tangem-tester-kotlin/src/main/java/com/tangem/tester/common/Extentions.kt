package com.tangem.tester.common

import com.tangem.commands.common.jsonConverter.MoshiJsonConverter
import kotlin.reflect.KClass

/**
[REDACTED_AUTHOR]
 */
fun Int.foreach(block: (Int) -> Unit) {
    if (this <= 0) return
    for (count in 0 until this) block(count)
}

fun Any?.isNumber(): Boolean {
    if (this is Number) return true
    if (this !is String) return false
    if (this.isEmpty()) return false
    this.forEach { if (!it.isDigit()) return false }
    return true
}

fun String.insertAt(position: Int, what: String): String {
    return this.replace("^(.{$position})", "$1$what");
}

fun String?.safeSplit(delimiter: String = "\\."): List<String>? {
    return this?.split(delimiter.toRegex())
}

inline fun <reified T> MoshiJsonConverter.listFromJson(json: String): List<T> {
    return fromJson<List<T>>(json, typedList(T::class.java)) ?: listOf()
}

fun MoshiJsonConverter.mapFromJson(json: String, key: KClass<*> = String::class, value: KClass<*> = Any::class): Map<String, Any> {
    return fromJson(json, typedMap(key, value)) ?: mapOf()
}
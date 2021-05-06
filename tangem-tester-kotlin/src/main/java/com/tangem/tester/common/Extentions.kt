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

inline fun <reified T> MoshiJsonConverter.listFromJson(json: String): List<T> {
    return fromJson<List<T>>(json, typedList(T::class.java)) ?: listOf()
}

fun MoshiJsonConverter.mapFromJson(json: String, key: KClass<*> = String::class, value: KClass<*> = Any::class): Map<String, Any> {
    return fromJson(json, typedMap(key, value)) ?: mapOf()
}
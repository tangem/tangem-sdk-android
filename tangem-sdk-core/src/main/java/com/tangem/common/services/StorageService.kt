package com.tangem.common.services

import kotlin.reflect.KClass

/**
[REDACTED_AUTHOR]
 */
interface StorageService {
    fun bool(forKey: Keys): Boolean
    fun set(boolValue: Boolean, forKey: Keys)
    fun string(forKey: Keys): String?
    fun set(stringValue: String, forKey: Keys)
    fun <T> obj(forKey: Keys, klass: KClass<*>, postfix: String? = null): T?
    fun <T> set(obj: T, forKey: Keys, postfix: String? = null)

    enum class Keys {
        HasSuccessfulTapIn,
        CardValues
    }
}

inline fun <reified T> StorageService.obj(forKey: StorageService.Keys, postfix: String? = null): T? {
    return obj(forKey, T::class, postfix)
}
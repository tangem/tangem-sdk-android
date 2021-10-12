package com.tangem.common.json

/**
[REDACTED_AUTHOR]
 */
interface JSONStringConvertible {
    fun toJson(): String = MoshiJsonConverter.INSTANCE.toJson(this)
}
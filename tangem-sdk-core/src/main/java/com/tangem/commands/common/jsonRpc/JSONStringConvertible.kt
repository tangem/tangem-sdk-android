package com.tangem.commands.common.jsonRpc

import com.tangem.commands.common.jsonConverter.MoshiJsonConverter

/**
[REDACTED_AUTHOR]
 */
interface JSONStringConvertible {
    fun toJson(): String = MoshiJsonConverter.INSTANCE.toJson(this)
}
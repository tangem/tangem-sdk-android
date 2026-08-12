package com.tangem.common

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.extensions.calculateSha256
import com.tangem.crypto.secureCompare

@JsonClass(generateAdapter = true)
class UserCode constructor(
    @Json(name = "type")
    val type: UserCodeType,
    @Json(name = "value")
    val value: ByteArray?,
) {
    constructor(type: UserCodeType) : this(type, type.defaultValue)

    constructor(type: UserCodeType, stringValue: String) : this(type, stringValue.calculateSha256())

    fun isDefault(): Boolean {
        if (value == null) return false
        return value.secureCompare(type.defaultValue.calculateSha256())
    }

    fun isNonDefault(): Boolean {
        // A null value means the code is unset/cleared (e.g. after DepersonalizeCommand), which is
        // not a user-set non-default code — require a present value so callers reading this as
        // "a non-default code is set" don't misclassify an unset code as set.
        return value != null && !isDefault()
    }

    companion object {
        const val DefaultAccessCode = "000000"
        const val DefaultPasscode = "000"
    }
}

@JsonClass(generateAdapter = false)
enum class UserCodeType(val defaultValue: String) {
    @Json(name = "AccessCode")
    AccessCode(UserCode.DefaultAccessCode),

    @Json(name = "Passcode")
    Passcode(UserCode.DefaultPasscode),
    ;

    fun isWrongPinEntered(environment: SessionEnvironment?): Boolean {
        environment ?: return false
        return when (this) {
            AccessCode -> !environment.isUserCodeSet(AccessCode)
            Passcode -> !environment.isUserCodeSet(Passcode)
        }
    }
}
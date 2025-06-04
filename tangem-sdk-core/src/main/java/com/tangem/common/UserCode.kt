package com.tangem.common

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.extensions.calculateSha256

@JsonClass(generateAdapter = true)
class UserCode constructor(
    @Json(name = "type")
    val type: UserCodeType,
    @Json(name = "value")
    val value: ByteArray?,
) {
    constructor(type: UserCodeType) : this(type, type.defaultValue)

    constructor(type: UserCodeType, stringValue: String) : this(type, stringValue.calculateSha256())

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

package com.tangem.operations.resetcode

import com.tangem.common.tlv.InteractionMode

enum class AuthorizeMode(override val rawValue: Byte) : InteractionMode {
    FileOwnerGetChallenge(rawValue = 0x01),
    FileOwnerAuthenticate(rawValue = 0x02),
    TokenGet(rawValue = 0x03),
    TokenSign(rawValue = 0x04),
    TokenAuthenticate(rawValue = 0x05),

    // COS v8+
    SecureDelay(rawValue = 0x10),
    AccessToken(rawValue = 0x11),
    AsymmetricKey(rawValue = 0x12),
    PinChallenge(rawValue = 0x21),
    PinResponse(rawValue = 0x22),
    ;

    companion object {
        private val values = entries.toTypedArray()
        fun byRawValue(rawValue: Int): AuthorizeMode? = values.find { it.rawValue.toInt() == rawValue }
    }
}
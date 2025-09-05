package com.tangem.common.tlv

import com.tangem.common.UserCode
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.calculateSha256

class TlvBuilder {
    private val tlvs = mutableListOf<Tlv>()
    private val encoder = TlvEncoder()

    internal inline fun <reified T> append(tag: TlvTag, value: T?) {
        if (value == null) return

        tlvs.add(encoder.encode(tag, value))
    }

    @Throws(TangemSdkError::class)
    fun appendPinIfNeeded(tag: TlvTag, userCode: UserCode, card: Card?): TlvBuilder {
        if (tag != TlvTag.Pin && tag != TlvTag.Pin2) {
            throw TangemSdkError.EncodingFailed("Wrong tag passed. Expected .pin or .pin2, got $tag")
        }

        if (card != null &&
            card.firmwareVersion >= FirmwareVersion.isDefaultPinsOptional &&
            userCode.value.contentEquals(userCode.type.defaultValue.calculateSha256())
        ) {
            return this
        }

        tlvs.add(encoder.encode(tag, userCode.value))
        return this
    }

    fun serialize(): ByteArray = tlvs.serialize()
}
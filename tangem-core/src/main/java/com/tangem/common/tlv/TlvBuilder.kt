package com.tangem.common.tlv

import com.tangem.Log
import com.tangem.MessageType
import com.tangem.TypedMessage

class TlvBuilder {
    private val tlvs = mutableListOf<Tlv>()
    private val encoder = TlvEncoder()

    internal inline fun <reified T> append(tag: TlvTag, value: T?) {
        if (value == null) return

        tlvs.add(encoder.encode(tag, value))
    }

    fun serialize(): ByteArray {
        Log.write(TypedMessage(MessageType.SEND_TLV, tlvs.joinToString("\n")))
        return tlvs.serialize()
    }

}
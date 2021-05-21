package com.tangem.common.extensions

import java.nio.ByteBuffer

fun Int.toByteArray(size: Int = Int.SIZE_BYTES): ByteArray {
    if (size == Int.SIZE_BYTES) {
        val buffer = ByteBuffer.allocate(size)
        buffer.putInt(this)
        return buffer.array()
    } else if (size == Short.SIZE_BYTES){
        return byteArrayOf(
                (this ushr 8).toByte(),
                this.toByte())
    } else if (size == 1) {
        return byteArrayOf(this.toByte())
    }
    return byteArrayOf()
}
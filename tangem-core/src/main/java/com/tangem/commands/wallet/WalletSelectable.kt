package com.tangem.commands.wallet

import com.tangem.common.extensions.toHexString
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvTag

/**
 * Pointer to specific wallet for interaction
 * Currently available two type of pointers: `Index` and `PublicKey`
 * Note: Available for cards with COS v.4.0 and higher
 */
sealed class WalletIndex {
    /**
     * Pointer to wallet by index.
     */
    data class Index(val index: Int) : WalletIndex() {
        override fun toString(): String = "Int wallet index $index"
    }

    /**
     * Pointer to wallet by wallet public key
     */
    data class PublicKey(val data: ByteArray) : WalletIndex() {
        override fun toString(): String = "Public key wallet index: ${data.toHexString()}"
    }
}

fun WalletIndex.addTlvData(tlvBuilder: TlvBuilder) {
    when (this) {
        is WalletIndex.Index -> index.let { tlvBuilder.append(TlvTag.WalletsIndex, it) }
        is WalletIndex.PublicKey -> data.let { tlvBuilder.append(TlvTag.WalletPublicKey, it) }
    }
}
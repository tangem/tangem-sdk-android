package com.tangem.commands

import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvTag

/**
 * Use this to identify that CardSessionRunnable type can point to specific wallet to interact with
 * Note: Available for cards with COS v.4.0 and higher
 */
interface WalletPointable {
    var walletPointer: WalletPointer?
}

/**
 * Pointer to specific wallet for interaction
 * Currently available two type of pointers: `WalletIndexPointer` and `WalletPublicKeyPointer`
 * Note: Available for cards with COS v.4.0 and higher
 */
interface WalletPointer {
    fun addTlvData(tlvBuilder: TlvBuilder)
}

/**
 * Pointer to wallet by index.
 * Note: Available for cards with COS v.4.0 and higher
 */
class WalletIndexPointer(var index: Int? = null) : WalletPointer {

    override fun addTlvData(tlvBuilder: TlvBuilder) {
        index?.let { tlvBuilder.append(TlvTag.WalletsIndex, it) }
    }
}

/**
 * Pointer to wallet by wallet public key
 * Note: Available for cards with COS v.4.0 and higher
 */
class WalletPublicKeyPointer : WalletPointer {
    var data: ByteArray? = null

    override fun addTlvData(tlvBuilder: TlvBuilder) {
        data?.let { tlvBuilder.append(TlvTag.WalletPublicKey, it) }
    }
}
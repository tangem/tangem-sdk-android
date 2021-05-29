package com.tangem.common.moshiJsonConverter

import com.tangem.commands.common.jsonConverter.MoshiJsonConverter
import com.tangem.commands.wallet.WalletIndex
import com.tangem.common.extensions.toHexString
import com.tangem.crypto.CryptoUtils
import org.junit.Test

class MoshiJsonConverterTest {
    private val moshi = MoshiJsonConverter.INSTANCE

    @Test
    fun arrayOfByteArray() {
        val data: Array<ByteArray> = (0..3).map { CryptoUtils.generateRandomBytes(10) }.toTypedArray()
        val jsonBytesArray = moshi.toJson(data, "")
        val byteArray: Array<ByteArray>? = moshi.fromJson(jsonBytesArray)
        assert(byteArray != null)

        val nullSafeByteArray = byteArray!!
        assert(data.size == nullSafeByteArray.size)
        val result = data.mapIndexed { index, bytes -> nullSafeByteArray[index].contentEquals(bytes) }
        assert(!result.contains(false))
    }

    @Test
    fun walletIndex() {
        val initialJsonIndex = "\"1\""
        val pubKeyBytes = CryptoUtils.generateRandomBytes(50)
        val initialJsonPubKeyHex = "\"${pubKeyBytes.toHexString()}\""

        val indexInt: WalletIndex.Index? = moshi.fromJson(initialJsonIndex)
        assert(indexInt != null)
        assert(indexInt!!.index == 1)
        assert(initialJsonIndex == moshi.toJson(indexInt))


        val indexPubKey: WalletIndex.PublicKey? = moshi.fromJson(initialJsonPubKeyHex)
        assert(indexPubKey != null)
        assert(indexPubKey!!.data.contentEquals(pubKeyBytes))
        assert(initialJsonPubKeyHex == moshi.toJson(indexPubKey))

        val walletIndexInt: WalletIndex? = moshi.fromJson(initialJsonIndex)
        assert(walletIndexInt != null)
        assert(walletIndexInt is WalletIndex.Index)
        assert((walletIndexInt as WalletIndex.Index).index == 1)
        assert(initialJsonIndex == moshi.toJson(walletIndexInt))

        val walletIndexPubKey: WalletIndex? = moshi.fromJson(initialJsonPubKeyHex)
        assert(walletIndexPubKey != null)
        assert(walletIndexPubKey is WalletIndex.PublicKey)
        assert((walletIndexPubKey as WalletIndex.PublicKey).data.contentEquals(pubKeyBytes))
        assert(initialJsonPubKeyHex == moshi.toJson(walletIndexPubKey))
    }
}
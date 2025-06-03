package com.tangem.common.services

import com.tangem.common.extensions.hexToBytes
import com.tangem.common.extensions.toHexString
import com.tangem.common.services.secure.SecureStorage

class InMemoryStorage : SecureStorage {
    private val byteArrayStorage = mutableMapOf<String, ByteArray>()

    override fun get(account: String): ByteArray? {
        return byteArrayStorage[account]
    }

    override fun getAsString(key: String): String? {
        return byteArrayStorage[key]?.toHexString()
    }

    override fun store(data: ByteArray, account: String) {
        byteArrayStorage[account] = data
    }

    override fun store(key: String, value: String) {
        byteArrayStorage[key] = value.hexToBytes()
    }

    override fun delete(account: String) {
        byteArrayStorage.remove(account)
    }

    override fun storeKey(key: ByteArray, account: String) {
        store(key, account)
    }
}
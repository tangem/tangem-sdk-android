package com.tangem.common.services.secure

/**
 * Created by Anton Zhilenkov on 06/10/2020.
 */
interface SecureStorage {

    fun get(account: String): ByteArray?

    fun getAsString(key: String): String?

    fun store(key: String, value: String)

    fun store(data: ByteArray, account: String)

    fun delete(account: String)

    fun storeKey(key: ByteArray, account: String)

    companion object
}

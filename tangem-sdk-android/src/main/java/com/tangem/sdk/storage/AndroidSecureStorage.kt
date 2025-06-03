package com.tangem.sdk.storage

import android.content.Context
import at.favre.lib.armadillo.Armadillo
import at.favre.lib.armadillo.ArmadilloSharedPreferences
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.extensions.toHexString
import com.tangem.common.services.secure.SecureStorage

/**
[REDACTED_AUTHOR]
 */
class AndroidSecureStorage(
    private val preferences: ArmadilloSharedPreferences,
) : SecureStorage {

    private val editor = preferences.edit()

    override fun get(account: String): ByteArray? {
        return preferences.getString(account, null)?.hexToBytes()
    }

    override fun getAsString(key: String): String? {
        return preferences.getString(key, null)
    }

    override fun store(data: ByteArray, account: String) {
        editor.putString(account, data.toHexString()).apply()
    }

    override fun store(key: String, value: String) {
        editor.putString(key, value).apply()
    }

    override fun delete(account: String) {
        editor.remove(account).apply()
    }

    override fun storeKey(key: ByteArray, account: String) {
        store(key, account)
    }
}

fun SecureStorage.Companion.createEncryptedSharedPreferences(
    context: Context,
    storageName: String,
): ArmadilloSharedPreferences = Armadillo.create(context, storageName).encryptionFingerprint(context).build()

fun SecureStorage.Companion.create(context: Context): SecureStorage {
    val sharedPreferences = createEncryptedSharedPreferences(context, "tangemSdkStorage")
    return AndroidSecureStorage(sharedPreferences)
}
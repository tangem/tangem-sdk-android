package com.tangem.sdk.storage

import android.content.Context
import at.favre.lib.armadillo.Armadillo
import at.favre.lib.armadillo.ArmadilloSharedPreferences
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.services.secure.SecureStorage

/**
[REDACTED_AUTHOR]
 */
@Deprecated(
    "Use AndroidSecureStorageV2 instead",
    replaceWith = ReplaceWith(
        "AndroidSecureStorageV2",
        "com.tangem.sdk.storage.AndroidSecureStorageV2",
    ),
)
class AndroidSecureStorage(
    private val preferences: ArmadilloSharedPreferences,
    private val androidSecureStorageV2: AndroidSecureStorageV2,
) : SecureStorage by androidSecureStorageV2 {

    private val editor = preferences.edit()

    override fun get(account: String): ByteArray? {
        return androidSecureStorageV2.get(account) ?: run {
            val value = preferences.getString(account, null)?.hexToBytes()
            if (value != null) {
                editor.remove(account).apply()
                androidSecureStorageV2.store(value, account)
            }
            value
        }
    }

    override fun getAsString(key: String): String? {
        return androidSecureStorageV2.getAsString(key) ?: run {
            val value = preferences.getString(key, null)
            if (value != null) {
                editor.remove(key).apply()
                androidSecureStorageV2.store(value, key)
            }
            value
        }
    }

    override fun delete(account: String) {
        editor.remove(account).apply()
        androidSecureStorageV2.delete(account)
    }
}

@Deprecated("Use AndroidSecureStorageV2 instead")
fun SecureStorage.Companion.createEncryptedSharedPreferences(
    context: Context,
    storageName: String,
): ArmadilloSharedPreferences = Armadillo.create(context, storageName).encryptionFingerprint(context).build()

@Deprecated("Use AndroidSecureStorageV2 instead")
fun SecureStorage.Companion.create(context: Context): SecureStorage {
    val sharedPreferences = createEncryptedSharedPreferences(context, "tangemSdkStorage")
    val androidSecureStorageV2 = AndroidSecureStorageV2(appContext = context, name = "tangemSdkStorage2")
    return AndroidSecureStorage(sharedPreferences, androidSecureStorageV2)
}
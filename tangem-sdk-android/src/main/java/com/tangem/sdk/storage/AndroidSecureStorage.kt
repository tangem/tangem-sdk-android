package com.tangem.sdk.storage

import android.content.Context
import at.favre.lib.armadillo.Armadillo
import at.favre.lib.armadillo.ArmadilloSharedPreferences
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.services.secure.SecureStorage

/**
 * AndroidSecureStorage is a deprecated secure storage implementation that combines two versions of secure storage.
 *
 * This class is a wrapper around two versions of secure storage implementations.
 * It first tries to get data from the latest version (AndroidSecureStorageV3).
 * If the data is not found, it attempts to retrieve it from the previous version (AndroidSecureStorageV2).
 * If the data is found in the previous version, it deletes it from there and stores it in the latest version.
 * If the data is not found in either version, it checks the ArmadilloSharedPreferences.
 **/
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
    private val androidSecureStorageV3: AndroidSecureStorageV2,
) : SecureStorage by androidSecureStorageV3 {

    private val editor = preferences.edit()

    override fun get(account: String): ByteArray? {
        return androidSecureStorageV3.get(account) ?: run {
            val v2Value = androidSecureStorageV2.get(account)
            if (v2Value != null) {
                androidSecureStorageV2.delete(account)
                androidSecureStorageV3.store(v2Value, account)
                v2Value
            } else {
                preferences.getString(account, null)?.hexToBytes()?.also { value ->
                    editor.remove(account).apply()
                    androidSecureStorageV3.store(value, account)
                }
            }
        }
    }

    override fun getAsString(key: String): String? {
        return androidSecureStorageV3.getAsString(key) ?: run {
            val v2Value = androidSecureStorageV2.getAsString(key)
            if (v2Value != null) {
                androidSecureStorageV2.delete(key)
                androidSecureStorageV3.store(v2Value, key)
                v2Value
            } else {
                preferences.getString(key, null)?.also { value ->
                    editor.remove(key).apply()
                    androidSecureStorageV3.store(value, key)
                }
            }
        }
    }

    override fun delete(account: String) {
        editor.remove(account).apply()
        androidSecureStorageV2.delete(account)
        androidSecureStorageV3.delete(account)
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
    val androidSecureStorageV2Legacy = AndroidSecureStorageV2(
        appContext = context,
        useStrongBox = true,
        name = "tangemSdkStorage2",
    )
    // This is the new storage implementation without StrongBox
    // On some devices, strongbox is laggy, so we have to provide new implementation without it
    val androidSecureStorageV3 = AndroidSecureStorageV2(
        appContext = context,
        useStrongBox = false,
        name = "tangemSdkStorage3",
    )
    return AndroidSecureStorage(
        preferences = sharedPreferences,
        androidSecureStorageV2 = androidSecureStorageV2Legacy,
        androidSecureStorageV3 = androidSecureStorageV3,
    )
}
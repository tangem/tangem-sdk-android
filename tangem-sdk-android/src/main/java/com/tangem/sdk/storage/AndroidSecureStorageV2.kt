package com.tangem.sdk.storage

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import com.tangem.common.services.secure.SecureStorage
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.core.content.edit

private const val AES_KEY_ALIAS_STRONG_BOX = "AndroidSecureStorageV2_aes_key"
private const val AES_KEY_ALIAS_HARDWARE = "AndroidSecureStorageV2_aes_key_hardware"
private const val ALGORITHM = "AES/GCM/NoPadding"
private const val IV_TAG_LENGTH_BIT = 128
private const val KEY_SIZE = 256

class AndroidSecureStorageV2(
    private val appContext: Context,
    private val useStrongBox: Boolean,
    name: String = "AndroidSecureStorageV2",
) : SecureStorage {

    private val aesKeyAlias = if (useStrongBox) {
        AES_KEY_ALIAS_STRONG_BOX
    } else {
        AES_KEY_ALIAS_HARDWARE
    }

    private val sharedPreferences = appContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun get(account: String): ByteArray? {
        return sharedPreferences.getString(account, null)?.let { encryptedData ->
            runCatching {
                decrypt(encryptedData.decodeBase64())
            }.getOrNull()
        }
    }

    override fun getAsString(key: String): String? {
        return sharedPreferences.getString(key, null)?.let { encryptedData ->
            runCatching {
                String(decrypt(encryptedData.decodeBase64()))
            }.getOrNull()
        }
    }

    override fun store(key: String, value: String) {
        storeEncrypted(value.toByteArray(), key)
    }

    override fun store(data: ByteArray, account: String) {
        storeEncrypted(data, account)
    }

    override fun delete(account: String) {
        sharedPreferences.edit { remove(account) }
    }

    override fun storeKey(key: ByteArray, account: String) {
        storeEncrypted(key, account)
    }

    private fun storeEncrypted(data: ByteArray, key: String) {
        val encryptedData = encrypt(data)
        sharedPreferences.edit {
            putString(key, encryptedData.base64())
        }
    }

    private fun encrypt(content: ByteArray): ByteArray {
        val aesKey = getAesKey()
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey)
        val encrypted = cipher.doFinal(content)
        val iv = cipher.iv
        return ByteBuffer.allocate(Int.SIZE_BYTES + iv.size + encrypted.size).apply {
            putInt(iv.size)
            put(iv)
            put(encrypted)
        }.array()
    }

    private fun decrypt(content: ByteArray): ByteArray {
        val aesKey = getAesKey()
        val cipher = Cipher.getInstance(ALGORITHM)
        val byteBuffer = ByteBuffer.wrap(content)
        val ivSize = byteBuffer.int
        val iv = ByteArray(ivSize)
        byteBuffer.get(iv)
        val encrypted = ByteArray(byteBuffer.remaining())
        byteBuffer.get(encrypted)
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(IV_TAG_LENGTH_BIT, iv))
        return cipher.doFinal(encrypted)
    }

    private fun ByteArray.base64(): String {
        return Base64.encodeToString(this, Base64.NO_WRAP)
    }

    private fun String.decodeBase64(): ByteArray {
        return Base64.decode(this, Base64.NO_WRAP)
    }

    private fun getAesKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return if (keyStore.containsAlias(aesKeyAlias).not()) {
            generateAesKey()
        } else {
            (keyStore.getEntry(aesKeyAlias, null) as KeyStore.SecretKeyEntry).secretKey
        }
    }

    private fun generateAesKey(): SecretKey {
        val keyGenerator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            aesKeyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setKeySize(KEY_SIZE)
            if (useStrongBoxFeature()) {
                setIsStrongBoxBacked(true)
            }
        }.build()
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    private fun useStrongBoxFeature(): Boolean {
        return useStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hasStrongBox()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun hasStrongBox(): Boolean {
        return appContext.packageManager
            .hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
    }
}
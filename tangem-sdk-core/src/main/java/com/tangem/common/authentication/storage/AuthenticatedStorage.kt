package com.tangem.common.authentication.storage

import com.tangem.Log
import com.tangem.common.authentication.keystore.KeystoreManager
import com.tangem.common.authentication.keystore.AuthenticationPolicy
import com.tangem.common.authentication.keystore.MasterKeyConfigs
import com.tangem.common.services.secure.SecureStorage
import com.tangem.crypto.operations.AESCipherOperations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.crypto.SecretKey

/**
 * A storage utility class that provides encryption and decryption capabilities for secure data persistence.
 *
 * @property secureStorage A storage utility to fetch and store encrypted data.
 * @property keystoreManager A manager to handle and provide encryption keys.
 */
class AuthenticatedStorage(
    private val secureStorage: SecureStorage,
    private val keystoreManager: KeystoreManager,
) {

    private val storeMutex = Mutex()

    /**
     * Retrieves and decrypts data from the storage after necessary user authentication.
     *
     * @param keyAlias The unique identifier for the stored encrypted data.
     * @param authenticationPolicy When the user is asked to authenticate.
     *
     * @return The decrypted data as [ByteArray] or `null` if data is not found.
     */
    suspend fun get(
        keyAlias: String,
        authenticationPolicy: AuthenticationPolicy = AuthenticationPolicy.Force,
    ): ByteArray? = withContext(Dispatchers.IO) {
        val encryptedData = secureStorage.get(keyAlias)
            ?.takeIf(ByteArray::isNotEmpty)

        if (encryptedData == null) {
            Log.warning {
                """
                    $TAG - Data not found in storage
                    |- Key: $keyAlias
                """.trimIndent()
            }

            return@withContext null
        }

        val (config, key) = getSecretKey(keyAlias, authenticationPolicy) ?: run {
            Log.warning {
                """
                    $TAG - The secret key is not stored
                    |- Key alias: $keyAlias
                """.trimIndent()
            }

            return@withContext null
        }

        val decryptedData = decrypt(keyAlias, key, encryptedData)

        migrateIfNeeded(keyAlias, decryptedData, config)

        decryptedData
    }

    private suspend fun getSecretKey(
        keyAlias: String,
        authenticationPolicy: AuthenticationPolicy,
    ): Pair<MasterKeyConfigs, SecretKey>? {
        val currentConfig = MasterKeyConfigs.current
        val dataKey = keystoreManager.get(currentConfig, keyAlias, authenticationPolicy)

        return if (dataKey == null) {
            MasterKeyConfigs.old.firstNotNullOfOrNull { masterKeyConfig ->
                val key = keystoreManager.get(masterKeyConfig, keyAlias, authenticationPolicy.forOldMasterKeys())
                    ?: return@firstNotNullOfOrNull null

                masterKeyConfig to key
            }
        } else {
            currentConfig to dataKey
        }
    }

    private suspend fun migrateIfNeeded(keyAlias: String, decryptedData: ByteArray, config: MasterKeyConfigs) {
        if (config.isCurrent) return

        val encryptedData = encrypt(keyAlias, decryptedData)
        secureStorage.store(encryptedData, keyAlias)
    }

    /**
     * Retrieves and decrypts data from the storage after necessary user authentication.
     *
     * @param keysAliases The unique identifiers for the stored encrypted data.
     * @param authenticationPolicy When the user is asked to authenticate.
     *
     * @return The decrypted data as a map of key-alias to [ByteArray] or an empty map if data is not found.
     */
    suspend fun get(
        keysAliases: Collection<String>,
        authenticationPolicy: AuthenticationPolicy = AuthenticationPolicy.Force,
    ): Map<String, ByteArray> = withContext(Dispatchers.IO) {
        val keyToEncryptedData = keysAliases
            .mapNotNull { keyAlias ->
                val data = secureStorage.get(keyAlias)
                    ?.takeIf(ByteArray::isNotEmpty)
                    ?: return@mapNotNull null

                keyAlias to data
            }
            .takeIf { it.isNotEmpty() }
            ?.toMap()

        if (keyToEncryptedData == null) {
            Log.warning {
                """
                    $TAG - Data not found in storage
                    |- Keys: $keysAliases
                """.trimIndent()
            }

            return@withContext emptyMap()
        }

        val (config, keys) = getSecretKeys(keyToEncryptedData.keys, authenticationPolicy) ?: run {
            Log.warning {
                """
                    $TAG - The secret keys are not stored
                    |- Key aliases: $keysAliases
                """.trimIndent()
            }

            return@withContext emptyMap()
        }

        val decryptedData = keyToEncryptedData
            .mapNotNull { (keyAlias, encryptedData) ->
                val key = keys[keyAlias] ?: return@mapNotNull null
                val decryptedData = decrypt(keyAlias, key, encryptedData)

                keyAlias to decryptedData
            }
            .toMap()

        migrateIfNeeded(decryptedData, config)

        decryptedData
    }

    private suspend fun getSecretKeys(
        keysAliases: Collection<String>,
        authenticationPolicy: AuthenticationPolicy,
    ): Pair<MasterKeyConfigs, Map<String, SecretKey>>? {
        val currentConfig = MasterKeyConfigs.current
        val dataKeys = keystoreManager.get(currentConfig, keysAliases.toSet(), authenticationPolicy)

        return if (dataKeys.isEmpty()) {
            MasterKeyConfigs.old.firstNotNullOfOrNull { masterKeyConfig ->
                val keys = keystoreManager.get(
                    masterKeyConfig,
                    keysAliases.toSet(),
                    authenticationPolicy.forOldMasterKeys(),
                )
                    .takeIf { it.isNotEmpty() }
                    ?: return@firstNotNullOfOrNull null

                masterKeyConfig to keys
            }
        } else {
            currentConfig to dataKeys
        }
    }

    /**
     * An old master key is only read to migrate its data, so it never forces a new authentication — and keeps
     * [AuthenticationPolicy.ReuseOnly] from prompting.
     */
    private fun AuthenticationPolicy.forOldMasterKeys(): AuthenticationPolicy = when (this) {
        AuthenticationPolicy.ReuseOnly -> AuthenticationPolicy.ReuseOnly
        AuthenticationPolicy.Force, AuthenticationPolicy.ReuseOrPrompt -> AuthenticationPolicy.ReuseOrPrompt
    }

    private fun decrypt(keyAlias: String, key: SecretKey, encryptedData: ByteArray): ByteArray {
        val iv = getDataIv(keyAlias)
        val decryptionCipher = AESCipherOperations.initDecryptionCipher(key, iv)

        return AESCipherOperations.decrypt(decryptionCipher, encryptedData)
    }

    private suspend fun migrateIfNeeded(decryptedData: Map<String, ByteArray>, config: MasterKeyConfigs) {
        if (config.isCurrent) return

        decryptedData.forEach { (keyAlias, data) ->
            val encryptedData = encrypt(keyAlias, data)
            secureStorage.store(encryptedData, keyAlias)
        }
    }

    /**
     * Encrypts and stores data securely in the storage.
     *
     * @param keyAlias The unique identifier which will be associated with the encrypted data.
     * @param data The plain data to be encrypted and stored.
     */
    suspend fun store(keyAlias: String, data: ByteArray) = storeMutex.withLock {
        withContext(Dispatchers.IO) {
            val encryptedData = encrypt(keyAlias, data)

            secureStorage.store(encryptedData, keyAlias)
        }
    }

    /**
     * Deletes the encrypted data associated with the provided key from storage.
     *
     * @param key The unique identifier of the encrypted data to be deleted.
     */
    fun delete(key: String) {
        secureStorage.delete(key)
    }

    private suspend fun encrypt(keyAlias: String, decryptedData: ByteArray): ByteArray {
        val key = generateAndStoreDataKey(keyAlias)
        val encryptionCipher = AESCipherOperations.initEncryptionCipher(key)
        val encryptedData = AESCipherOperations.encrypt(encryptionCipher, decryptedData)

        storeDataIv(keyAlias, encryptionCipher.iv)

        return encryptedData
    }

    private suspend fun generateAndStoreDataKey(keyAlias: String): SecretKey {
        val dataKey = AESCipherOperations.generateKey()

        keystoreManager.store(MasterKeyConfigs.current, keyAlias, dataKey)

        return dataKey
    }

    private fun getDataIv(dataKeyAlias: String): ByteArray {
        val iv = secureStorage.get(getStorageKeyForDataIv(dataKeyAlias))

        return requireNotNull(iv) {
            val msg = "Unable to find IV for data decryption: $dataKeyAlias"

            Log.error { "$TAG - $msg" }
            msg
        }
    }

    private fun storeDataIv(dataKeyAlias: String, initializationVector: ByteArray) {
        secureStorage.store(
            data = initializationVector,
            account = getStorageKeyForDataIv(dataKeyAlias),
        )
    }

    private fun getStorageKeyForDataIv(dataKeyAlias: String): String = "data_iv_$dataKeyAlias"

    private companion object {
        const val TAG = "Authenticated Storage"
    }
}
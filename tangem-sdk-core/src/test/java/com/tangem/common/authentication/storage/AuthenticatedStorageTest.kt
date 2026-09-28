package com.tangem.common.authentication.storage

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.tangem.common.authentication.keystore.AuthenticationPolicy
import com.tangem.common.authentication.keystore.KeystoreManager
import com.tangem.common.authentication.keystore.MasterKeyConfigs
import com.tangem.common.services.secure.SecureStorage
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.spongycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import javax.crypto.SecretKey

internal class AuthenticatedStorageTest {

    @Before
    fun setup() {
        Security.addProvider(BouncyCastleProvider())
    }

    @Test
    fun `GIVEN stored data WHEN get without a policy THEN authentication is forced`() = runTest {
        val keystoreManager = RecordingKeystoreManager()
        val storage = createStorage(keystoreManager)
        storage.store(KEY_ALIAS, DATA)

        val data = storage.get(KEY_ALIAS)

        assertThat(data).isEqualTo(DATA)
        assertThat(keystoreManager.requests)
            .containsExactly(MasterKeyConfigs.current.alias to AuthenticationPolicy.Force)
    }

    @Test
    fun `GIVEN stored data WHEN get with a policy THEN the current master key is read with it`() =
        forEachPolicy { policy ->
            val keystoreManager = RecordingKeystoreManager()
            val storage = createStorage(keystoreManager)
            storage.store(KEY_ALIAS, DATA)

            val data = storage.get(KEY_ALIAS, policy)

            assertWithMessage(policy.name).that(data).isEqualTo(DATA)
            assertWithMessage(policy.name).that(keystoreManager.requests)
                .containsExactly(MasterKeyConfigs.current.alias to policy)
        }

    @Test
    fun `GIVEN stored data WHEN get several keys with a policy THEN the current master key is read with it`() =
        forEachPolicy { policy ->
            val keystoreManager = RecordingKeystoreManager()
            val storage = createStorage(keystoreManager)
            storage.store(KEY_ALIAS, DATA)

            val data = storage.get(listOf(KEY_ALIAS), policy)

            assertWithMessage(policy.name).that(data.keys).containsExactly(KEY_ALIAS)
            assertWithMessage(policy.name).that(keystoreManager.requests)
                .containsExactly(MasterKeyConfigs.current.alias to policy)
        }

    @Test
    fun `GIVEN data under an old master key WHEN get with a policy THEN the old key is never forced`() =
        forEachPolicy { policy ->
            val keystoreManager = RecordingKeystoreManager()
            val storage = createStorage(keystoreManager)
            val oldMasterKeyAlias = MasterKeyConfigs.old.first().alias
            storage.store(KEY_ALIAS, DATA)
            keystoreManager.moveKeysTo(oldMasterKeyAlias)
            keystoreManager.requests.clear()

            storage.get(KEY_ALIAS, policy)

            val expectedPolicy = if (policy == AuthenticationPolicy.ReuseOnly) {
                AuthenticationPolicy.ReuseOnly
            } else {
                AuthenticationPolicy.ReuseOrPrompt
            }
            assertWithMessage(policy.name).that(keystoreManager.requests).containsAtLeast(
                MasterKeyConfigs.current.alias to policy,
                oldMasterKeyAlias to expectedPolicy,
            ).inOrder()
        }

    private fun forEachPolicy(test: suspend (AuthenticationPolicy) -> Unit) = runTest {
        AuthenticationPolicy.entries.forEach { policy -> test(policy) }
    }

    private fun createStorage(keystoreManager: KeystoreManager) =
        AuthenticatedStorage(secureStorage = InMemorySecureStorage(), keystoreManager = keystoreManager)

    private class RecordingKeystoreManager : KeystoreManager {

        private val keys = mutableMapOf<Pair<String, String>, SecretKey>()
        val requests = mutableListOf<Pair<String, AuthenticationPolicy>>()

        fun moveKeysTo(masterKeyAlias: String) {
            val moved = keys.mapKeys { (key, _) -> masterKeyAlias to key.second }
            keys.clear()
            keys.putAll(moved)
        }

        override suspend fun get(
            masterKeyConfig: KeystoreManager.MasterKeyConfig,
            keyAlias: String,
            authenticationPolicy: AuthenticationPolicy,
        ): SecretKey? {
            requests += masterKeyConfig.alias to authenticationPolicy
            return keys[masterKeyConfig.alias to keyAlias]
        }

        override suspend fun get(
            masterKeyConfig: KeystoreManager.MasterKeyConfig,
            keyAliases: Set<String>,
            authenticationPolicy: AuthenticationPolicy,
        ): Map<String, SecretKey> {
            requests += masterKeyConfig.alias to authenticationPolicy
            return keyAliases
                .mapNotNull { alias -> keys[masterKeyConfig.alias to alias]?.let { alias to it } }
                .toMap()
        }

        override suspend fun store(masterKeyConfig: KeystoreManager.MasterKeyConfig, keyAlias: String, key: SecretKey) {
            keys[masterKeyConfig.alias to keyAlias] = key
        }
    }

    private class InMemorySecureStorage : SecureStorage {

        private val values = mutableMapOf<String, ByteArray>()

        override fun get(account: String): ByteArray? = values[account]

        override fun getAsString(key: String): String? = values[key]?.decodeToString()

        override fun store(key: String, value: String) {
            values[key] = value.encodeToByteArray()
        }

        override fun store(data: ByteArray, account: String) {
            values[account] = data
        }

        override fun delete(account: String) {
            values.remove(account)
        }

        override fun storeKey(key: ByteArray, account: String) {
            values[account] = key
        }
    }

    private companion object {
        const val KEY_ALIAS = "key"
        val DATA = byteArrayOf(1, 2, 3)
    }
}
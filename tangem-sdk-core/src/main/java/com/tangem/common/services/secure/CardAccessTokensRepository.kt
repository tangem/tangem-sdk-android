package com.tangem.common.services.secure

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.tangem.Log
import com.tangem.common.authentication.keystore.KeystoreManager
import com.tangem.common.authentication.storage.AuthenticatedStorage
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.extensions.mapNotNullValues
import com.tangem.common.extensions.toHexString
import com.tangem.common.json.MoshiJsonConverter
import com.tangem.common.v8.CardAccessTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class CardAccessTokensRepository(
    keystoreManager: KeystoreManager,
    private val secureStorage: SecureStorage,
) {

    private val authenticatedStorage = AuthenticatedStorage(
        secureStorage = secureStorage,
        keystoreManager = keystoreManager,
    )

    private val moshi: Moshi = MoshiJsonConverter.INSTANCE.moshi
    private val cardsIdsAdapter: JsonAdapter<Set<String>> = moshi.adapter(
        Types.newParameterizedType(Set::class.java, String::class.java),
    )

    private val tokens: ConcurrentHashMap<String, CardAccessTokens> = ConcurrentHashMap()

    suspend fun save(cardAccessTokens: CardAccessTokens, cardIds: List<String>) = withContext(Dispatchers.IO) {
        if (cardIds.isEmpty()) return@withContext
        val savedCardIds = getSavedCardIds().toMutableSet()

        for (cardId in cardIds) {
            try {
                val storageKey = StorageKey.CardAccessTokens(cardId).name
                authenticatedStorage.delete(storageKey)

                val data = cardAccessTokens.encode()
                try {
                    authenticatedStorage.store(storageKey, data)
                    Log.info { "saved cardAccessTokens: ${data.size}" }
                } finally {
                    data.fill(0)
                }

                savedCardIds.add(cardId)
                tokens[cardId] = cardAccessTokens
            } catch (e: Exception) {
                Log.debug { "Card access tokens error for cardId: $cardId" }
            }
        }

        saveCardIds(savedCardIds)
        Log.debug { "Card access tokens saved successfully" }
    }

    suspend fun save(cardAccessTokens: CardAccessTokens, cardId: String) {
        save(cardAccessTokens, listOf(cardId))
    }

    fun deleteTokens(cardIds: Collection<String>) {
        if (cardIds.isEmpty()) return

        val savedCardIds = getSavedCardIds().toMutableSet()

        for (cardId in cardIds) {
            if (!savedCardIds.contains(cardId)) continue

            val storageKey = StorageKey.CardAccessTokens(cardId).name
            authenticatedStorage.delete(storageKey)
            savedCardIds.remove(cardId)
            tokens.remove(cardId)
        }

        saveCardIds(savedCardIds)
        Log.debug { "Card access tokens deletion completed successfully" }
    }

    suspend fun clear() {
        Log.debug { "Clear CardAccessTokensRepository" }
        try {
            val cardIds = getSavedCardIds()
            deleteTokens(cardIds)
        } catch (e: Exception) {
            Log.error { e.toString() }
        }
    }

    fun contains(cardId: String): Boolean {
        return try {
            getSavedCardIds().contains(cardId)
        } catch (e: Exception) {
            Log.error { e.toString() }
            false
        }
    }

    suspend fun unlock() {
        tokens.clear()
        Log.debug { "Start unlocking card access tokens" }

        val fetchedTokens = hashMapOf<String, CardAccessTokens>()

        val savedCardIds = getSavedCardIds()
        val keys = savedCardIds.map {
            StorageKey.CardAccessTokens(it).name
        }
        val encodedData = authenticatedStorage.get(keys)

        try {
            val cardTokens = encodedData.mapKeys { (key, _) -> key.removePrefix(StorageKey.CardAccessTokens.PREFIX) }
                .mapNotNullValues { (_, value) -> decodeCardAccessTokens(value) }
            fetchedTokens.putAll(cardTokens)
        } catch (e: Exception) {
            Log.debug {
                "Failed to unlock card access tokens for cardIds: ${
                    savedCardIds.joinToString(";") { it }
                }. Error: $e"
            }
        } finally {
            encodedData.values.forEach { it.fill(0) }
        }

        saveCardIds(fetchedTokens.keys)
        tokens.putAll(fetchedTokens)

        Log.debug { "Card access tokens unlocked successfully" }
    }

    fun lock() {
        Log.debug { "Lock the card access tokens repo" }
        tokens.clear()
    }

    fun hasSavedCardTokens(cardId: String): Boolean {
        return getSavedCardIds().contains(cardId)
    }

    fun hasSavedCardTokens(): Boolean {
        return getSavedCardIds().isNotEmpty()
    }

    fun fetch(cardId: String): CardAccessTokens? {
        return tokens[cardId]
    }

    private fun getSavedCardIds(): Set<String> {
        Log.info { "getSavedCardIds size" }
        val data = secureStorage.get(StorageKey.CardsWithSavedAccessTokens.name)
            ?: return emptySet()
        return data.decodeToString(throwOnInvalidSequence = true)
            .let {
                cardsIdsAdapter.fromJson(it).also { cardIds ->
                    Log.info { "getSavedCardIds size: ${cardIds?.size}" }
                }
            }
            .orEmpty()
    }

    private fun saveCardIds(cardIds: Collection<String>) {
        Log.info { "saveCardIds size: ${cardIds.size}" }
        val data = cardIds.toSet()
            .let(cardsIdsAdapter::toJson)
            .encodeToByteArray(throwOnInvalidSequence = true)
        secureStorage.store(data, StorageKey.CardsWithSavedAccessTokens.name)
    }

    private sealed interface StorageKey {
        val name: String

        class CardAccessTokens(cardId: String) : StorageKey {
            override val name: String = PREFIX + cardId

            companion object {
                const val PREFIX = "card_access_tokens_"
            }
        }

        object CardsWithSavedAccessTokens : StorageKey {
            override val name: String = "cards_with_saved_access_tokens"
        }
    }
}

private const val TOKEN_SEPARATOR = "|"

private fun CardAccessTokens.encode(): ByteArray {
    return (accessToken.toHexString() + TOKEN_SEPARATOR + identifyToken.toHexString())
        .encodeToByteArray(throwOnInvalidSequence = true)
}

private fun decodeCardAccessTokens(data: ByteArray): CardAccessTokens {
    val str = data.decodeToString(throwOnInvalidSequence = true)
    val separatorIndex = str.indexOf(TOKEN_SEPARATOR)
    require(separatorIndex > 0 && separatorIndex < str.length - 1) { "Invalid CardAccessTokens data" }
    return CardAccessTokens(
        accessToken = str.take(separatorIndex).hexToBytes(),
        identifyToken = str.substring(separatorIndex + 1).hexToBytes(),
    )
}
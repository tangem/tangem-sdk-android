package com.tangem.common.services

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.tangem.common.extensions.toHexString
import com.tangem.common.services.secure.SecureStorage
import com.tangem.operations.attestation.ArtworkSize
import java.util.concurrent.TimeUnit

class ArtworksStorage(
    private val storage: SecureStorage,
    moshi: Moshi,
) {

    private val adapter = createMoshiAdapter(moshi)
    private val data = mutableMapOf<String, ArtworkData>()

    init {
        fetch()
    }

    fun get(cardId: String, cardPublicKey: ByteArray, size: ArtworkSize): ByteArray? {
        val artworkData = data[getStorageKey(cardId, cardPublicKey, size)] ?: return null
        return if (System.currentTimeMillis() < artworkData.timestamp + TimeUnit.DAYS.toMillis(CACHE_LIFE_TIME)) {
            artworkData.artwork.toByteArray()
        } else {
            null
        }
    }

    fun store(cardId: String, cardPublicKey: ByteArray, size: ArtworkSize, artwork: ByteArray) {
        val key = getStorageKey(cardId, cardPublicKey, size)
        data[key] = ArtworkData(artwork.toList(), System.currentTimeMillis())
        storage.store(key = KEY, value = adapter.toJson(data))
    }

    private fun fetch() {
        val json = storage.getAsString(key = KEY) ?: return
        val storedData = adapter.fromJson(json) ?: return

        this.data.putAll(storedData)
    }

    private fun createMoshiAdapter(moshi: Moshi): JsonAdapter<Map<String, ArtworkData>> {
        return moshi.adapter(
            Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                ArtworkData::class.java,
            ),
        )
    }

    private fun getStorageKey(cardId: String, cardPublicKey: ByteArray, size: ArtworkSize): String {
        return "${cardId}_${cardPublicKey.toHexString()}_${size.ordinal}"
    }

    private companion object {
        const val CACHE_LIFE_TIME = 7L
        const val KEY = "card_artworks"
    }

    @JsonClass(generateAdapter = true)
    data class ArtworkData(
        val artwork: List<Byte>,
        val timestamp: Long,
    )
}
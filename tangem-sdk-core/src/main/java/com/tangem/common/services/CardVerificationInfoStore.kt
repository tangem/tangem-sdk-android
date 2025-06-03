package com.tangem.common.services

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.tangem.common.extensions.calculateSha256
import com.tangem.common.extensions.toHexString
import com.tangem.common.services.secure.SecureStorage
import com.tangem.operations.attestation.api.models.CardVerificationInfoResponse
import kotlin.collections.set

/**
 * Store of [CardVerificationInfoResponse]
 *
 * @property storage secure storage
 * @param moshi moshi
 */
internal class CardVerificationInfoStore(
    private val storage: SecureStorage,
    moshi: Moshi,
) {

    private val adapter = createMoshiAdapter(moshi)

    // Key is Hash of card's public key
    private val data = mutableMapOf<String, CardVerificationInfoResponse>()

    init {
        fetch()
    }

    fun get(cardPublicKey: ByteArray): CardVerificationInfoResponse? {
        val hexHash = cardPublicKey.calculateSha256().toHexString()
        return data[hexHash]
    }

    fun store(cardPublicKey: ByteArray, response: CardVerificationInfoResponse) {
        val hexHash = cardPublicKey.calculateSha256().toHexString()

        data[hexHash] = response

        storage.store(key = KEY, value = adapter.toJson(data))
    }

    private fun fetch() {
        val json = storage.getAsString(key = KEY) ?: return

        val storedData = adapter.fromJson(json) ?: return

        this.data.putAll(storedData)
    }

    private fun createMoshiAdapter(moshi: Moshi): JsonAdapter<Map<String, CardVerificationInfoResponse>> {
        return moshi.adapter(
            Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                CardVerificationInfoResponse::class.java,
            ),
        )
    }

    private companion object {

        const val KEY = "card_verification_info_map"
    }
}
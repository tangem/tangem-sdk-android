package com.tangem.operations.attestation.api

import com.tangem.common.extensions.toHexString
import com.tangem.common.json.MoshiJsonConverter
import com.tangem.common.services.Result
import com.tangem.common.services.performRequest
import com.tangem.operations.attestation.api.models.CardArtworksResponse
import com.tangem.operations.attestation.api.models.CardVerificationInfoResponse
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Tangem API service
 *
 * @property isProdEnvironment flag that determines whether to use prod or dev API environment
 *
[REDACTED_AUTHOR]
 */
internal class TangemApiService(private val isProdEnvironment: Boolean) {

    private val tangemTechApi: TangemTechApi by lazy(::create)

    suspend fun getOnlineAttestationResponse(
        cardId: String,
        cardPublicKey: ByteArray,
    ): Result<CardVerificationInfoResponse> {
        return performRequest {
            val baseUrl = if (isProdEnvironment) BaseUrl.CARD_DATA.url else BaseUrl.CARD_DATA_DEV.url

            tangemTechApi.getCardVerificationInfo(
                url = baseUrl + "card",
                cardId = cardId,
                publicKey = cardPublicKey.toHexString(),
            )
        }
    }

    suspend fun loadArtwork(cardId: String, cardPublicKey: ByteArray): Result<CardArtworksResponse> {
        return performRequest {
            val baseUrl = if (isProdEnvironment) BaseUrl.CARD_DATA.url else BaseUrl.CARD_DATA_DEV.url

            tangemTechApi.getCardArtworks(
                url = baseUrl + "card/artworks",
                cardId = cardId,
                publicKey = cardPublicKey.toHexString(),
            )
        }
    }

    private fun create(): TangemTechApi {
        val builder = Retrofit.Builder()
            .baseUrl(BaseUrl.CARD_DATA.url)
            .addConverterFactory(MoshiConverterFactory.create(MoshiJsonConverter.INSTANCE.moshi))
            .client(
                OkHttpClient.Builder()
                    .apply {
                        TangemApiServiceLogging.apiInterceptors.forEach { addInterceptor(it) }
                    }
                    .build(),

            )
            .build()

        return builder.create(TangemTechApi::class.java)
    }
}
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
 * @property baseUrl base URL for the API service
 *
[REDACTED_AUTHOR]
 */
internal class TangemApiService(private val baseUrlProvider: () -> String) {

    private val baseUrl: String get() = baseUrlProvider()

    private val tangemTechApi: TangemTechApi by lazy(::create)

    suspend fun getOnlineAttestationResponse(
        cardId: String,
        cardPublicKey: ByteArray,
    ): Result<CardVerificationInfoResponse> {
        if (baseUrl.isBlank()) {
            return Result.Failure(IllegalArgumentException("Base URL is not set"))
        }

        return try {
            val response = tangemTechApi.getCardVerificationInfo(
                url = baseUrl + "card",
                cardId = cardId,
                publicKey = cardPublicKey.toHexString(),
            )

            Result.Success(response)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    suspend fun loadArtwork(cardId: String, cardPublicKey: ByteArray): Result<CardArtworksResponse> {
        if (baseUrl.isBlank()) {
            return Result.Failure(IllegalArgumentException("Base URL is not set"))
        }

        return performRequest {
            tangemTechApi.getCardArtworks(
                url = baseUrl + "card/artworks",
                cardId = cardId,
                publicKey = cardPublicKey.toHexString(),
            )
        }
    }

    private fun create(): TangemTechApi {
        val builder = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(MoshiConverterFactory.create(MoshiJsonConverter.INSTANCE.moshi))
            .client(
                OkHttpClient.Builder()
                    .apply {
                        TangemApiServiceSettings.apiInterceptors.forEach(::addInterceptor)
                    }
                    .build(),
            )
            .build()

        return builder.create(TangemTechApi::class.java)
    }
}
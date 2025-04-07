package com.tangem.operations.attestation.api

import com.tangem.operations.attestation.api.models.CardArtworksResponse
import com.tangem.operations.attestation.api.models.CardVerificationInfoResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Url

internal interface TangemTechApi {

    @Headers("Content-Type: application/json")
    @GET
    suspend fun getCardVerificationInfo(
        @Url url: String,
        @Header("card_id") cardId: String,
        @Header("card_public_key") publicKey: String,
    ): CardVerificationInfoResponse

    @Headers("Content-Type: application/json")
    @GET("card/artworks")
    suspend fun getCardArtworks(
        @Header("card_id") cardId: String,
        @Header("card_public_key") publicKey: String,
    ): CardArtworksResponse
}
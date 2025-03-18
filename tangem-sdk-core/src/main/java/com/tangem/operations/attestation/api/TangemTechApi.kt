package com.tangem.operations.attestation.api

import com.tangem.operations.attestation.api.models.CardVerificationInfoResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers

internal interface TangemTechApi {

    @Headers("Content-Type: application/json")
    @GET("card")
    suspend fun getCardVerificationInfo(
        @Header("card_id") cardId: String,
        @Header("card_public_key") publicKey: String,
    ): CardVerificationInfoResponse
}
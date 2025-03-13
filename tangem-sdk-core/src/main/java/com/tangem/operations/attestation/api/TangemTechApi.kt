package com.tangem.operations.attestation.api

import com.tangem.operations.attestation.api.models.CardDataResponse
import com.tangem.operations.attestation.api.models.CardVerificationInfoResponse
import retrofit2.http.*

internal interface TangemTechApi {

    @Deprecated("Use getCardVerificationInfo instead")
    @Headers("Content-Type: application/json")
    @GET("card")
    suspend fun getCardData(@HeaderMap headers: Map<String, String>): CardDataResponse

    @Headers("Content-Type: application/json")
    @POST("card")
    suspend fun getCardVerificationInfo(
        @Header("card_id") cardId: String,
        @Header("card_public_key") publicKey: String,
    ): CardVerificationInfoResponse

    companion object {

        fun getCardDataHeaders(cardId: String, publicKey: String): Map<String, String> {
            return mapOf("card_id" to cardId, "card_public_key" to publicKey)
        }
    }
}
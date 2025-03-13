package com.tangem.operations.attestation.api

import com.tangem.operations.attestation.api.models.CardVerifyAndGetInfo
import okhttp3.ResponseBody
import retrofit2.http.*

internal interface TangemVerifyApi {

    @Headers("Content-Type: application/json")
    @POST("card/verify-and-get-info")
    suspend fun getCardVerifyAndGetInfo(@Body requestBody: CardVerifyAndGetInfo.Request): CardVerifyAndGetInfo.Response

    @Headers("Content-Type: application/json")
    @GET("card/artwork")
    suspend fun getArtwork(
        @Query("artworkId") artworkId: String,
        @Query("CID") cid: String,
        @Query("publicKey") publicKey: String,
    ): ResponseBody
}
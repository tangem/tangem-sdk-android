package com.tangem.commands.common.network


import com.tangem.commands.common.network.ApiTangem.Companion.ARTWORK
import com.tangem.commands.common.network.ApiTangem.Companion.VERIFY_AND_GET_INFO
import com.tangem.commands.verifycard.CardVerifyAndGetInfo
import okhttp3.ResponseBody
import retrofit2.http.*

interface TangemApi {

    @Headers("Content-Type: application/json")
    @POST(VERIFY_AND_GET_INFO)
    suspend fun getCardVerifyAndGetInfo(
            @Body requestBody: CardVerifyAndGetInfo.Request
    ): CardVerifyAndGetInfo.Response

    @Headers("Content-Type: application/json")
    @GET(ARTWORK)
    suspend fun getArtwork(
            @Query("artworkId") artworkId: String,
            @Query("CID") CID: String,
            @Query("publicKey") publicKey: String
    ): ResponseBody
}
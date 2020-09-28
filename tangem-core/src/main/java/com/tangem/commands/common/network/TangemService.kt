package com.tangem.commands.common.network

import com.tangem.commands.verifycard.CardVerifyAndGetInfo
import okhttp3.ResponseBody

class TangemService {

    private val tangemApi: TangemApi by lazy {
        createRetrofitInstance(ApiTangem.TANGEM_ENDPOINT).create(TangemApi::class.java)
    }

    suspend fun verifyAndGetInfo(
            cardId: String,
            cardPublicKey: String
    ): Result<CardVerifyAndGetInfo.Response> {
        val requestsBody = CardVerifyAndGetInfo.Request()
        requestsBody.requests =
                listOf(CardVerifyAndGetInfo.Request.Item(cardId, cardPublicKey))

        return performRequest { tangemApi.getCardVerifyAndGetInfo(requestsBody) }
    }

    suspend fun getArtwork(
            cardId: String,
            cardPublicKey: String,
            artworkId: String
    ): Result<ResponseBody> {
        return performRequest { tangemApi.getArtwork(artworkId, cardId, cardPublicKey) }
    }

    companion object {
        fun getUrlForArtwork(cardId: String, cardPublicKey: String, artworkId: String): String {
            return ApiTangem.TANGEM_ENDPOINT + ApiTangem.ARTWORK +
                    "?artworkId=${artworkId}&CID=${cardId}&publicKey=$cardPublicKey"
        }
    }

}
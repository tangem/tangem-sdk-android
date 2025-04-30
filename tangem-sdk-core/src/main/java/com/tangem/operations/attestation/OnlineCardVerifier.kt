package com.tangem.operations.attestation

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tangem.Log
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.toHexString
import com.tangem.common.services.CardVerificationInfoStore
import com.tangem.common.services.Result
import com.tangem.common.services.performRequest
import com.tangem.operations.attestation.api.BaseUrl
import com.tangem.operations.attestation.api.TangemTechApi
import com.tangem.operations.attestation.api.TangemVerifyApi
import com.tangem.operations.attestation.api.models.CardVerificationInfoResponse
import com.tangem.operations.attestation.api.models.CardVerifyAndGetInfo
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.PrintWriter
import java.io.StringWriter

class OnlineCardVerifier {

    val scope = CoroutineScope(Dispatchers.IO) + CoroutineExceptionHandler { _, ex ->
        val sw = StringWriter()
        ex.printStackTrace(PrintWriter(sw))
        Log.error { sw.toString() }
    }

    private val tangemVerifyApi: TangemVerifyApi by lazy {
        val builder = Retrofit.Builder()
            .baseUrl(BaseUrl.VERIFY.url)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return@lazy builder.create(TangemVerifyApi::class.java)
    }

    private val tangemTechApi: TangemTechApi by lazy {
        val builder = Retrofit.Builder()
            .baseUrl(BaseUrl.CARD_DATA.url)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return@lazy builder.create(TangemTechApi::class.java)
    }

    suspend fun getCardInfo(cardId: String, cardPublicKey: ByteArray): Result<CardVerifyAndGetInfo.Response.Item> {
        val requestsBody = CardVerifyAndGetInfo.Request(
            requests = listOf(
                CardVerifyAndGetInfo.Request.Item(cid = cardId, publicKey = cardPublicKey.toHexString()),
            ),
        )

        return when (val result = performRequest { tangemVerifyApi.getCardVerifyAndGetInfo(requestsBody) }) {
            is Result.Success -> {
                val firstResult = result.data.results?.firstOrNull()
                when {
                    firstResult == null -> Result.Failure(TangemSdkError.NetworkError(customMessage = "Empty response"))
                    !firstResult.passed -> Result.Failure(TangemSdkError.CardVerificationFailed())
                    else -> Result.Success(firstResult)
                }
            }
            is Result.Failure -> result
        }
    }

    internal suspend fun getCardVerificationInfo(
        isProdEnvironment: Boolean,
        cardId: String,
        cardPublicKey: ByteArray,
        cardVerificationInfoStore: CardVerificationInfoStore,
    ): Result<CardVerificationInfoResponse> {
        return performRequest {
            val baseUrl = if (isProdEnvironment) BaseUrl.CARD_DATA.url else BaseUrl.CARD_DATA_DEV.url

            tangemTechApi.getCardVerificationInfo(
                url = baseUrl + "card",
                cardId = cardId,
                publicKey = cardPublicKey.toHexString(),
            )
        }.also {
            if (it is Result.Success) {
                cardVerificationInfoStore.store(cardPublicKey = cardPublicKey, response = it.data)
            }
        }
    }

    companion object {
        private val moshi: Moshi by lazy {
            Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
        }
    }
}
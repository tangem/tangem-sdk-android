package com.tangem.operations.attestation.service

import com.tangem.Log
import com.tangem.common.CompletionResult
import com.tangem.common.core.TangemSdkError
import com.tangem.common.json.MoshiJsonConverter
import com.tangem.common.services.CardVerificationInfoStore
import com.tangem.common.services.Result
import com.tangem.common.services.secure.SecureStorage
import com.tangem.common.services.toTangemSdkError
import com.tangem.operations.attestation.api.TangemApiService
import com.tangem.operations.attestation.api.models.CardVerificationInfoResponse
import com.tangem.operations.attestation.verification.OnlineAttestationVerifier
import retrofit2.HttpException
import java.io.EOFException

internal class CommonOnlineAttestationService(
    private val tangemApiService: TangemApiService,
    private val onlineAttestationVerifier: OnlineAttestationVerifier,
    private val cardVerificationInfoStore: CardVerificationInfoStore,
) : OnlineAttestationService {

    constructor(
        tangemApiService: TangemApiService,
        onlineAttestationVerifier: OnlineAttestationVerifier,
        secureStorage: SecureStorage,
    ) : this(tangemApiService, onlineAttestationVerifier, createDefaultStore(secureStorage))

    override suspend fun attestCard(
        cardId: String,
        cardPublicKey: ByteArray,
    ): CompletionResult<CardVerificationInfoResponse> {
        return when (val result = getAttestationData(cardId, cardPublicKey)) {
            is Result.Success -> {
                val isVerified = onlineAttestationVerifier.verify(response = result.data)

                if (isVerified) {
                    cardVerificationInfoStore.store(cardPublicKey = cardPublicKey, response = result.data)
                    CompletionResult.Success(result.data)
                } else {
                    Log.error { "Failed to verify attestation data" }

                    CompletionResult.Failure(TangemSdkError.CardVerificationFailed())
                }
            }
            is Result.Failure -> {
                Log.error { "Failed to get attestation data: ${result.error}" }

                if (result.error.isDataNotFoundException() || result.error.isEmptyResponseBody()) {
                    CompletionResult.Failure(error = TangemSdkError.CardVerificationFailed())
                } else {
                    CompletionResult.Failure(error = result.toTangemSdkError())
                }
            }
        }
    }

    private suspend fun getAttestationData(
        cardId: String,
        cardPublicKey: ByteArray,
    ): Result<CardVerificationInfoResponse> {
        val cache = cardVerificationInfoStore.get(cardPublicKey)

        if (cache != null) return Result.Success(data = cache)

        return tangemApiService.getOnlineAttestationResponse(cardId, cardPublicKey)
    }

    private fun Throwable.isDataNotFoundException(): Boolean {
        return this is HttpException && code() in DATA_NOT_FOUND_ERROR_CODES
    }

    private fun Throwable.isEmptyResponseBody(): Boolean = this is EOFException

    private companion object {

        val DATA_NOT_FOUND_ERROR_CODES = setOf(403, 404)

        fun createDefaultStore(secureStorage: SecureStorage): CardVerificationInfoStore {
            return CardVerificationInfoStore(
                storage = secureStorage,
                moshi = MoshiJsonConverter.INSTANCE.moshi,
            )
        }
    }
}
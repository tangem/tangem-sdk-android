package com.tangem.operations.attestation.service

import com.tangem.common.CompletionResult
import com.tangem.common.core.TangemSdkError
import com.tangem.common.core.toTangemSdkError
import com.tangem.common.json.MoshiJsonConverter
import com.tangem.common.services.CardVerificationInfoStore
import com.tangem.common.services.Result
import com.tangem.common.services.secure.SecureStorage
import com.tangem.common.services.toTangemSdkError
import com.tangem.operations.attestation.api.TangemApiService
import com.tangem.operations.attestation.api.models.CardVerificationInfoResponse
import com.tangem.operations.attestation.verification.OnlineAttestationVerifier

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
            is CompletionResult.Success -> {
                val isVerified = onlineAttestationVerifier.verify(response = result.data)

                if (isVerified) {
                    cardVerificationInfoStore.store(cardPublicKey = cardPublicKey, response = result.data)
                    result
                } else {
                    CompletionResult.Failure(TangemSdkError.CardVerificationFailed())
                }
            }
            is CompletionResult.Failure -> CompletionResult.Failure(result.error.toTangemSdkError())
        }
    }

    private suspend fun getAttestationData(
        cardId: String,
        cardPublicKey: ByteArray,
    ): CompletionResult<CardVerificationInfoResponse> {
        val cache = cardVerificationInfoStore.get(cardPublicKey)

        if (cache != null) return CompletionResult.Success(data = cache)

        return when (val result = tangemApiService.getOnlineAttestationResponse(cardId, cardPublicKey)) {
            is Result.Failure -> CompletionResult.Failure(error = result.toTangemSdkError())
            is Result.Success -> CompletionResult.Success(data = result.data)
        }
    }

    private companion object {

        fun createDefaultStore(secureStorage: SecureStorage): CardVerificationInfoStore {
            return CardVerificationInfoStore(
                storage = secureStorage,
                moshi = MoshiJsonConverter.INSTANCE.moshi,
            )
        }
    }
}
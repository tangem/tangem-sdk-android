package com.tangem.operations.attestation.service

import com.google.common.truth.Truth
import com.tangem.common.CompletionResult
import com.tangem.common.core.TangemSdkError
import com.tangem.common.services.CardVerificationInfoStore
import com.tangem.common.services.Result
import com.tangem.common.successOrNull
import com.tangem.operations.attestation.api.TangemApiService
import com.tangem.operations.attestation.api.models.CardVerificationInfoResponse
import com.tangem.operations.attestation.verification.OnlineAttestationVerifier
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
[REDACTED_AUTHOR]
 */
internal class CommonOnlineAttestationServiceTest {

    private val tangemApiService: TangemApiService = mockk()
    private val onlineAttestationVerifier: OnlineAttestationVerifier = mockk()
    private val cardVerificationInfoStore: CardVerificationInfoStore = mockk()

    private val service = CommonOnlineAttestationService(
        tangemApiService = tangemApiService,
        onlineAttestationVerifier = onlineAttestationVerifier,
        cardVerificationInfoStore = cardVerificationInfoStore,
    )

    /**
     * Cache state:  empty
     * API result:   success
     * Verification: success
     *
     * Result:       success
     */
    @Test
    fun test_1() = runTest {
        every { cardVerificationInfoStore.get(cardPublicKey = cardPublicKey) } returns null
        coEvery { tangemApiService.getOnlineAttestationResponse(CARD_ID, cardPublicKey) } returns
            Result.Success(response)
        every { onlineAttestationVerifier.verify(response = response) } returns true
        every { cardVerificationInfoStore.store(cardPublicKey = cardPublicKey, response = response) } just Runs

        val actual = service.attestCard(CARD_ID, cardPublicKey)

        coVerifyOrder {
            cardVerificationInfoStore.get(cardPublicKey = cardPublicKey)
            tangemApiService.getOnlineAttestationResponse(CARD_ID, cardPublicKey)
            onlineAttestationVerifier.verify(response = response)
            cardVerificationInfoStore.store(cardPublicKey = cardPublicKey, response = response)
        }

        Truth.assertThat(actual).isInstanceOf(CompletionResult.Success::class.java)
        Truth.assertThat(actual.successOrNull()).isEqualTo(response)
    }

    /**
     * Cache state:  has cached response
     * API result:   never mind
     * Verification: success
     *
     * Result:       success
     */
    @Test
    fun test_2() = runTest {
        every { cardVerificationInfoStore.get(cardPublicKey = cardPublicKey) } returns response
        every { onlineAttestationVerifier.verify(response = response) } returns true
        every { cardVerificationInfoStore.store(cardPublicKey = cardPublicKey, response = response) } just Runs

        val actual = service.attestCard(CARD_ID, cardPublicKey)

        coVerifyOrder {
            cardVerificationInfoStore.get(cardPublicKey = cardPublicKey)
            onlineAttestationVerifier.verify(response = response)
            cardVerificationInfoStore.store(cardPublicKey = cardPublicKey, response = response)
        }

        coVerify(inverse = true) {
            tangemApiService.getOnlineAttestationResponse(any(), any())
        }

        Truth.assertThat(actual).isInstanceOf(CompletionResult.Success::class.java)
        Truth.assertThat(actual.successOrNull()).isEqualTo(response)
    }

    /**
     * Cache state:  empty
     * API result:   failure
     * Verification: never mind
     *
     * Result:       failure
     */
    @Test
    fun test_3() = runTest {
        every { cardVerificationInfoStore.get(cardPublicKey = cardPublicKey) } returns null
        coEvery { tangemApiService.getOnlineAttestationResponse(CARD_ID, cardPublicKey) } returns Result.Failure(
            apiError,
        )

        val actual = service.attestCard(CARD_ID, cardPublicKey)

        coVerifyOrder {
            cardVerificationInfoStore.get(cardPublicKey = cardPublicKey)
            tangemApiService.getOnlineAttestationResponse(CARD_ID, cardPublicKey)
        }

        coVerify(inverse = true) {
            onlineAttestationVerifier.verify(response = any())
            cardVerificationInfoStore.store(cardPublicKey = any(), response = any())
        }

        Truth.assertThat(actual).isInstanceOf(CompletionResult.Failure::class.java)
        Truth.assertThat((actual as CompletionResult.Failure).error)
            .isEqualTo(apiError)
    }

    /**
     * Cache state:  empty
     * API result:   success
     * Verification: failure
     *
     * Result:       failure
     */
    @Test
    fun test_4() = runTest {
        every { cardVerificationInfoStore.get(cardPublicKey = cardPublicKey) } returns null
        coEvery { tangemApiService.getOnlineAttestationResponse(CARD_ID, cardPublicKey) } returns
            Result.Success(response)
        every { onlineAttestationVerifier.verify(response = response) } returns false

        val actual = service.attestCard(CARD_ID, cardPublicKey)

        coVerifyOrder {
            cardVerificationInfoStore.get(cardPublicKey = cardPublicKey)
            tangemApiService.getOnlineAttestationResponse(CARD_ID, cardPublicKey)
            onlineAttestationVerifier.verify(response = response)
        }

        verify(inverse = true) {
            cardVerificationInfoStore.store(cardPublicKey = any(), response = any())
        }

        Truth.assertThat(actual).isInstanceOf(CompletionResult.Failure::class.java)
        Truth.assertThat((actual as CompletionResult.Failure).error)
            .isInstanceOf(TangemSdkError.CardVerificationFailed::class.java)
    }

    /**
     * Cache state:  has cached response
     * API result:   never mind
     * Verification: failure
     *
     * Result:       failure
     */
    @Test
    fun test_5() = runTest {
        every { cardVerificationInfoStore.get(cardPublicKey = cardPublicKey) } returns response
        every { onlineAttestationVerifier.verify(response = response) } returns false

        val actual = service.attestCard(CARD_ID, cardPublicKey)

        coVerifyOrder {
            cardVerificationInfoStore.get(cardPublicKey = cardPublicKey)
            onlineAttestationVerifier.verify(response = response)
        }

        coVerify(inverse = true) {
            tangemApiService.getOnlineAttestationResponse(any(), any())
            cardVerificationInfoStore.store(cardPublicKey = any(), response = any())
        }

        Truth.assertThat(actual).isInstanceOf(CompletionResult.Failure::class.java)
        Truth.assertThat((actual as CompletionResult.Failure).error)
            .isInstanceOf(TangemSdkError.CardVerificationFailed::class.java)
    }

    private companion object {
        const val CARD_ID = "card_id"
        val cardPublicKey = byteArrayOf(1, 2, 3)

        val response = CardVerificationInfoResponse(
            manufacturerSignature = "manufacturerSignature",
            issuerSignature = "issuerSignature",
        )

        val apiError = TangemSdkError.NetworkError("Network error. Cause: ${IllegalStateException().localizedMessage}")
    }
}
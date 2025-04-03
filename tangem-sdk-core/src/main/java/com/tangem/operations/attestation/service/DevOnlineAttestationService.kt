package com.tangem.operations.attestation.service

import com.tangem.common.CompletionResult
import com.tangem.common.catching
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.extensions.toHexString
import com.tangem.crypto.sign
import com.tangem.operations.attestation.api.models.CardVerificationInfoResponse

internal object DevOnlineAttestationService : OnlineAttestationService {

    override suspend fun attestCard(
        cardId: String,
        cardPublicKey: ByteArray,
    ): CompletionResult<CardVerificationInfoResponse> {
        return catching { makeDevResponse(cardPublicKey) }
    }

    private fun makeDevResponse(cardPublicKey: ByteArray): CardVerificationInfoResponse {
        return CardVerificationInfoResponse(
            manufacturerSignature = byteArrayOf().toHexString(),
            issuerSignature = getDevIssuerSignature(cardPublicKey).toHexString(),
        )
    }

    private fun getDevIssuerSignature(cardPublicKey: ByteArray): ByteArray {
        val issuerPrivateKey = "11121314151617184771ED81F2BACF57479E4735EB1405083927372D40DA9E92".hexToBytes()

        return cardPublicKey.sign(issuerPrivateKey)
    }
}
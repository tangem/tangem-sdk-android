package com.tangem.operations.backup

import com.tangem.common.CompletionResult
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.guard
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.json.MoshiJsonConverter
import com.tangem.common.services.CardVerificationInfoStore
import com.tangem.common.services.Result
import com.tangem.common.services.secure.SecureStorage
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvTag
import com.tangem.crypto.sign
import com.tangem.operations.attestation.OnlineCardVerifier
import com.tangem.operations.attestation.api.models.CardVerificationInfoResponse

internal class BackupCertificateProvider(
    secureStorage: SecureStorage,
    private val isNewAttestationEnabled: Boolean,
    private val isTangemAttestationProdEnv: Boolean,
) {

    private val onlineCardVerifier: OnlineCardVerifier by lazy(::OnlineCardVerifier)

    private val cardVerificationInfoStore by lazy {
        CardVerificationInfoStore(
            storage = secureStorage,
            moshi = MoshiJsonConverter.INSTANCE.moshi,
        )
    }

    suspend fun getCertificate(
        cardId: String,
        cardPublicKey: ByteArray,
        developmentMode: Boolean,
        callback: CompletionCallback<ByteArray>,
    ) {
        if (developmentMode) {
            val certificate = generateCertificate(
                cardPublicKey = cardPublicKey,
                issuerSignature = getDevIssuerSignature(cardPublicKey),
            )

            callback(CompletionResult.Success(certificate))

            return
        }

        val result = onlineCardVerifier.getCardVerificationInfo(
            isProdEnvironment = isTangemAttestationProdEnv,
            cardId = cardId,
            cardPublicKey = cardPublicKey,
            cardVerificationInfoStore = cardVerificationInfoStore,
        )

        if (isNewAttestationEnabled) {
            handleGetCertificateNewWay(result, cardPublicKey, callback)
        } else {
            handleGetCertificateOldWay(result, cardPublicKey, callback)
        }
    }

    private fun handleGetCertificateNewWay(
        result: Result<CardVerificationInfoResponse>,
        cardPublicKey: ByteArray,
        callback: CompletionCallback<ByteArray>,
    ) {
        val issuerSignature = (result as? Result.Success)?.data?.issuerSignature
            ?: cardVerificationInfoStore.get(cardPublicKey)?.issuerSignature

        val completionResult = if (issuerSignature != null) {
            val certificate = generateCertificate(cardPublicKey, issuerSignature.hexToBytes())

            CompletionResult.Success(data = certificate)
        } else {
            CompletionResult.Failure(TangemSdkError.IssuerSignatureLoadingFailed())
        }

        callback(completionResult)
    }

    private fun handleGetCertificateOldWay(
        result: Result<CardVerificationInfoResponse>,
        cardPublicKey: ByteArray,
        callback: CompletionCallback<ByteArray>,
    ) {
        when (result) {
            is Result.Success -> {
                val signature = result.data.issuerSignature.guard {
                    callback(CompletionResult.Failure(TangemSdkError.IssuerSignatureLoadingFailed()))
                    return
                }

                val certificate = generateCertificate(cardPublicKey, signature.hexToBytes())

                callback(CompletionResult.Success(data = certificate))
            }
            is Result.Failure -> callback(CompletionResult.Failure(TangemSdkError.IssuerSignatureLoadingFailed()))
        }
    }

    private fun getDevIssuerSignature(cardPublicKey: ByteArray): ByteArray {
        val issuerPrivateKey = "11121314151617184771ED81F2BACF57479E4735EB1405083927372D40DA9E92".hexToBytes()

        return cardPublicKey.sign(issuerPrivateKey)
    }

    private fun generateCertificate(cardPublicKey: ByteArray, issuerSignature: ByteArray): ByteArray {
        return TlvBuilder().run {
            append(TlvTag.CardPublicKey, cardPublicKey)
            append(TlvTag.IssuerDataSignature, issuerSignature)
            serialize()
        }
    }
}
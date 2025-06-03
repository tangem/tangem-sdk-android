package com.tangem.operations.backup

import com.tangem.common.CompletionResult
import com.tangem.common.card.FirmwareVersion
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
import com.tangem.operations.attestation.api.TangemApiService
import com.tangem.operations.attestation.api.models.CardVerificationInfoResponse
import com.tangem.operations.attestation.service.OnlineAttestationServiceFactory

internal class BackupCertificateProvider(
    secureStorage: SecureStorage,
    private val isNewAttestationEnabled: Boolean,
    private val isTangemAttestationProdEnv: Boolean,
) {

    private val onlineCardVerifier: OnlineCardVerifier by lazy(::OnlineCardVerifier)

    private val factory: OnlineAttestationServiceFactory by lazy {
        OnlineAttestationServiceFactory(
            tangemApiService = TangemApiService(isProdEnvironment = isTangemAttestationProdEnv),
            secureStorage = secureStorage,
        )
    }

    private val cardVerificationInfoStore by lazy {
        CardVerificationInfoStore(
            storage = secureStorage,
            moshi = MoshiJsonConverter.INSTANCE.moshi,
        )
    }

    @Suppress("LongParameterList")
    suspend fun getCertificate(
        cardId: String,
        cardPublicKey: ByteArray,
        issuerPublicKey: ByteArray,
        manufacturerName: String,
        firmwareVersion: FirmwareVersion,
        callback: CompletionCallback<ByteArray>,
    ) {
        if (isNewAttestationEnabled) {
            val service = factory.create(
                cardPublicKey = cardPublicKey,
                issuerPublicKey = issuerPublicKey,
                manufacturerName = manufacturerName,
                firmwareVersion = firmwareVersion,
            )

            when (val result = service.attestCard(cardId, cardPublicKey)) {
                is CompletionResult.Success -> {
                    val signature = result.data.issuerSignature.guard {
                        callback(CompletionResult.Failure(TangemSdkError.IssuerSignatureLoadingFailed()))
                        return
                    }

                    val certificate = generateCertificate(cardPublicKey, signature.hexToBytes())

                    callback(CompletionResult.Success(data = certificate))
                }
                is CompletionResult.Failure -> {
                    callback(CompletionResult.Failure(TangemSdkError.IssuerSignatureLoadingFailed()))
                }
            }
        } else {
            if (firmwareVersion.type == FirmwareVersion.FirmwareType.Sdk) {
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

            handleGetCertificateOldWay(result, cardPublicKey, callback)
        }
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
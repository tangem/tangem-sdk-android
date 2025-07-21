package com.tangem.operations.backup

import com.tangem.common.CompletionResult
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.Config
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.guard
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.services.secure.SecureStorage
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvTag
import com.tangem.operations.attestation.api.TangemApiService
import com.tangem.operations.attestation.service.OnlineAttestationServiceFactory

internal class BackupCertificateProvider(
    secureStorage: SecureStorage,
    private val config: Config,
) {

    private val factory: OnlineAttestationServiceFactory by lazy {
        OnlineAttestationServiceFactory(
            tangemApiService = TangemApiService { config.tangemApiBaseUrl },
            secureStorage = secureStorage,
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
    }

    private fun generateCertificate(cardPublicKey: ByteArray, issuerSignature: ByteArray): ByteArray {
        return TlvBuilder().run {
            append(TlvTag.CardPublicKey, cardPublicKey)
            append(TlvTag.IssuerDataSignature, issuerSignature)
            serialize()
        }
    }
}
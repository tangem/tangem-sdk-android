package com.tangem.operations.attestation.service

import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.services.secure.SecureStorage
import com.tangem.operations.attestation.api.TangemApiService
import com.tangem.operations.attestation.verification.ManufacturerPublicKeyProvider
import com.tangem.operations.attestation.verification.OnlineAttestationVerifier

/**
 * Factory for creating [OnlineAttestationService]
 *
 * @property tangemApiService Tangem API service
 * @property secureStorage    secure storage
 *
[REDACTED_AUTHOR]
 */
internal class OnlineAttestationServiceFactory(
    private val tangemApiService: TangemApiService,
    private val secureStorage: SecureStorage,
) {

    fun create(card: Card): OnlineAttestationService {
        return create(
            cardPublicKey = card.cardPublicKey,
            issuerPublicKey = card.issuer.publicKey,
            manufacturerName = card.manufacturer.name,
            firmwareVersion = card.firmwareVersion,
        )
    }

    fun create(
        cardPublicKey: ByteArray,
        issuerPublicKey: ByteArray,
        manufacturerName: String,
        firmwareVersion: FirmwareVersion,
    ): OnlineAttestationService {
        val isDevelopmentCard = firmwareVersion.type == FirmwareVersion.FirmwareType.Sdk

        if (isDevelopmentCard) return DevOnlineAttestationService

        val manufacturerPublicKeyProvider = ManufacturerPublicKeyProvider(
            firmwareVersion = firmwareVersion,
            manufacturerName = manufacturerName,
        )

        val verifier = OnlineAttestationVerifier(
            cardPublicKey = cardPublicKey,
            issuerPublicKey = issuerPublicKey,
            manufacturerKeyProvider = manufacturerPublicKeyProvider,
        )

        return CommonOnlineAttestationService(
            tangemApiService = tangemApiService,
            onlineAttestationVerifier = verifier,
            secureStorage = secureStorage,
        )
    }
}
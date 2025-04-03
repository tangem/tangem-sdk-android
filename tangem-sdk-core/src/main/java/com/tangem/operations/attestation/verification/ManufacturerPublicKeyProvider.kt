package com.tangem.operations.attestation.verification

import com.tangem.common.card.FirmwareVersion

/**
 * Provider of [ManufacturerPublicKey]
 *
 * @property firmwareVersion  firmware version
 * @property manufacturerName manufacturer name
 *
[REDACTED_AUTHOR]
 */
class ManufacturerPublicKeyProvider(
    private val firmwareVersion: FirmwareVersion,
    private val manufacturerName: String,
) {

    fun get(): ManufacturerPublicKey? {
        if (firmwareVersion < FirmwareVersion.KeysImportAvailable) return ManufacturerPublicKey.Tangem

        return ManufacturerPublicKey.values().firstOrNull { manufacturerName == it.id }
    }
}
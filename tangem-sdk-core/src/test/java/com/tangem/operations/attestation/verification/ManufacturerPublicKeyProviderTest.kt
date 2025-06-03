package com.tangem.operations.attestation.verification

import com.google.common.truth.Truth
import com.tangem.common.card.FirmwareVersion
import com.tangem.operations.attestation.verification.OnlineAttestationVerifierTest.Model
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
[REDACTED_AUTHOR]
 */
@RunWith(Parameterized::class)
internal class ManufacturerPublicKeyProviderTest(private val model: Model) {

    @Test
    fun test() {
        val provider = ManufacturerPublicKeyProvider(
            firmwareVersion = model.firmwareVersion,
            manufacturerName = model.manufacturerName,
        )

        val actual = provider.get()

        Truth.assertThat(actual).isEqualTo(model.expected)
    }

    data class Model(
        val firmwareVersion: FirmwareVersion,
        val manufacturerName: String,
        val expected: ManufacturerPublicKey?,
    )

    private companion object {

        @JvmStatic
        @Parameterized.Parameters
        fun data(): Collection<Model> = listOf(
            Model(
                firmwareVersion = FirmwareVersion(version = "6.20r"),
                manufacturerName = "TANGEM SDK", // never mind
                expected = ManufacturerPublicKey.Tangem,
            ),
            Model(
                firmwareVersion = FirmwareVersion.KeysImportAvailable, // 6.21
                manufacturerName = "TANGEM SDK",
                expected = ManufacturerPublicKey.TangemSDK,
            ),
            Model(
                firmwareVersion = FirmwareVersion.KeysImportAvailable, // 6.21
                manufacturerName = "TANGEM",
                expected = ManufacturerPublicKey.Tangem,
            ),
            Model(
                firmwareVersion = FirmwareVersion.Ed25519Slip0010Available,
                manufacturerName = "SMART CASH",
                expected = ManufacturerPublicKey.SmartCash,
            ),
            Model(
                firmwareVersion = FirmwareVersion.Ed25519Slip0010Available,
                manufacturerName = "SMART CASH SDK",
                expected = ManufacturerPublicKey.SmartCashSDK,
            ),
            Model(
                firmwareVersion = FirmwareVersion.Ed25519Slip0010Available,
                manufacturerName = "error",
                expected = null,
            ),
            Model(
                firmwareVersion = FirmwareVersion.Ed25519Slip0010Available,
                manufacturerName = "",
                expected = null,
            ),
        )
    }
}
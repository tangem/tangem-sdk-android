package com.tangem.operations.attestation.service

import com.google.common.truth.Truth
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.services.InMemoryStorage
import io.mockk.mockk
import org.junit.Test

/**
[REDACTED_AUTHOR]
 */
internal class OnlineAttestationServiceFactoryTest {

    private val factory = OnlineAttestationServiceFactory(
        tangemApiService = mockk(),
        secureStorage = InMemoryStorage(),
    )

    @Test
    fun `create dev service`() {
        val actual = factory.create(
            cardPublicKey = byteArrayOf(1, 2, 3),
            issuerPublicKey = byteArrayOf(1, 2, 3),
            manufacturerName = "never mind",
            firmwareVersion = FirmwareVersion(version = "6.21d SDK"),
        )

        Truth.assertThat(actual).isEqualTo(DevOnlineAttestationService)
    }

    @Test
    fun `create common service`() {
        val actual = factory.create(
            cardPublicKey = byteArrayOf(1, 2, 3),
            issuerPublicKey = byteArrayOf(1, 2, 3),
            manufacturerName = "never mind",
            firmwareVersion = FirmwareVersion(version = "6.21r"),
        )

        Truth.assertThat(actual).isInstanceOf(CommonOnlineAttestationService::class.java)
    }
}
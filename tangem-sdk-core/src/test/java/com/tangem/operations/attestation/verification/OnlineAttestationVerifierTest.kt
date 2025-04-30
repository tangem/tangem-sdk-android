package com.tangem.operations.attestation.verification

import com.google.common.truth.Truth
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.extensions.hexToBytes
import com.tangem.operations.attestation.api.models.CardVerificationInfoResponse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.spongycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
[REDACTED_AUTHOR]
 */
@RunWith(Parameterized::class)
internal class OnlineAttestationVerifierTest(private val model: Model) {

    private val verifier = OnlineAttestationVerifier(
        cardPublicKey = CARD_PUBLIC_KEY.hexToBytes(),
        issuerPublicKey = ISSUER_PUBLIC_KEY.hexToBytes(),
        manufacturerKeyProvider = ManufacturerPublicKeyProvider(
            firmwareVersion = FirmwareVersion(version = "3.05r"),
            manufacturerName = MANUFACTURER_NAME,
        ),
    )

    @Before
    fun setup() {
        Security.addProvider(BouncyCastleProvider())
    }

    @Test
    fun test() {
        val actual = verifier.verify(model.response)

        Truth.assertThat(actual).isEqualTo(model.expected)
    }

    data class Model(val response: CardVerificationInfoResponse, val expected: Boolean)

    private companion object {

        const val CARD_ID = "CB41000000001251"

        const val CARD_PUBLIC_KEY = "04DECC5E22EBD007B8A35D6F64C02B3AF554F25F43BE2BEC26BEFA57863D3D8ECE28B9826AC795B" +
            "8CC88C35980C53C82921D04623FCDDFDF41A579ED11507F71F7"

        const val ISSUER_PUBLIC_KEY = "048196AA4B410AC44A3B9CCE18E7BE226AEA070ACC83A9CF67540FAC49AF25129F6A538A28AD6" +
            "341358E3C4F9963064F7E365372A651D374E5C23CDD37FD099BF2"

        const val MANUFACTURER_NAME = "TANGEM"

        const val MANUFACTURER_SIGNATURE = "780110E038108B4C7965565F951629897FA50C6B1D03C21E6410A5AE16DB5533F1792605" +
            "02986E85633801C30DBC57B7F9C03E35F55CDA750D42ED5EE8E643B3"

        const val ISSUER_SIGNATURE = "5AD492E6B2C5E0EB25BE3C87A43ABEF2679A885840B888A9B912D1AA511C6CB5CD3D411725CE70" +
            "BF84545F9D4075A7B8F7D3E6C7A1C9C71BC9C377FAA7215BB3"

        @JvmStatic
        @Parameterized.Parameters
        fun data(): Collection<Model> = listOf(
            Model(
                response = CardVerificationInfoResponse(MANUFACTURER_SIGNATURE, ISSUER_SIGNATURE),
                expected = true,
            ),
            Model(
                response = CardVerificationInfoResponse(manufacturerSignature = null, issuerSignature = null),
                expected = false,
            ),
            Model(
                response = CardVerificationInfoResponse(MANUFACTURER_SIGNATURE, issuerSignature = null),
                expected = false,
            ),
            Model(
                response = CardVerificationInfoResponse(manufacturerSignature = null, ISSUER_SIGNATURE),
                expected = false,
            ),
            Model(
                response = CardVerificationInfoResponse(manufacturerSignature = "", issuerSignature = ""),
                expected = false,
            ),
            Model(
                response = CardVerificationInfoResponse(MANUFACTURER_SIGNATURE, issuerSignature = ""),
                expected = false,
            ),
            Model(
                response = CardVerificationInfoResponse(manufacturerSignature = "", ISSUER_SIGNATURE),
                expected = false,
            ),
            Model(
                response = CardVerificationInfoResponse(manufacturerSignature = "qwerty", ISSUER_SIGNATURE),
                expected = false,
            ),
            Model(
                response = CardVerificationInfoResponse(MANUFACTURER_SIGNATURE, issuerSignature = "qwerty"),
                expected = false,
            ),
        )
    }
}
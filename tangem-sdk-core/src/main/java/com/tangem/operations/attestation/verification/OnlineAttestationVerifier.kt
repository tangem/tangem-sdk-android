package com.tangem.operations.attestation.verification

import com.tangem.common.extensions.hexToBytes
import com.tangem.crypto.CryptoUtils
import com.tangem.crypto.Secp256k1Key
import com.tangem.operations.attestation.api.models.CardVerificationInfoResponse

/**
 * Online attestation verifier
 *
 * @property cardPublicKey           card public key
 * @property issuerPublicKey         issuer public key
 * @property manufacturerKeyProvider manufacturer public key provider
 *
[REDACTED_AUTHOR]
 */
internal class OnlineAttestationVerifier(
    private val cardPublicKey: ByteArray,
    private val issuerPublicKey: ByteArray,
    private val manufacturerKeyProvider: ManufacturerPublicKeyProvider,
) {

    fun verify(response: CardVerificationInfoResponse): Boolean {
        if (response.issuerSignature == null || response.manufacturerSignature == null) return false

        val isIssuerCertVerified by lazy {
            verifyIssuerCertificate(certificate = response.manufacturerSignature)
        }

        val isCardCertVerified by lazy {
            verifyCardCertificate(certificate = response.issuerSignature)
        }

        return isIssuerCertVerified && isCardCertVerified
    }

    private fun verifyIssuerCertificate(certificate: String): Boolean {
        val manufacturerPublicKey = manufacturerKeyProvider.get() ?: return false
        val compressedIssuerPublicKey = Secp256k1Key(issuerPublicKey).compress()

        return CryptoUtils.verify(
            publicKey = manufacturerPublicKey.value.hexToBytes(),
            message = compressedIssuerPublicKey,
            signature = certificate.hexToBytes(),
        )
    }

    private fun verifyCardCertificate(certificate: String): Boolean {
        return CryptoUtils.verify(
            publicKey = issuerPublicKey,
            message = cardPublicKey,
            signature = certificate.hexToBytes(),
        )
    }
}
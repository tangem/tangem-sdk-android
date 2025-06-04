package com.tangem.operations.attestation.verification

import com.tangem.common.extensions.hexToBytesOrNull
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
 * @author Andrew Khokhlov on 03/04/2025
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
        return verify(
            publicKey = {
                val manufacturerPublicKey = manufacturerKeyProvider.get()
                manufacturerPublicKey?.value?.hexToBytesOrNull() ?: return false
            },
            message = { Secp256k1Key(issuerPublicKey).compress() },
            signature = certificate,
        )
    }

    private fun verifyCardCertificate(certificate: String): Boolean {
        return verify(
            publicKey = { issuerPublicKey },
            message = { cardPublicKey },
            signature = certificate,
        )
    }

    private inline fun verify(publicKey: () -> ByteArray, message: () -> ByteArray, signature: String): Boolean {
        return runCatching {
            CryptoUtils.verify(
                publicKey = publicKey(),
                message = message(),
                signature = signature.hexToBytesOrNull() ?: return false,
            )
        }
            .getOrDefault(defaultValue = false)
    }
}

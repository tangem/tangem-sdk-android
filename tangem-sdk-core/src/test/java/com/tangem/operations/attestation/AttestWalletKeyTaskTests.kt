package com.tangem.operations.attestation

import com.google.common.truth.Truth.assertThat
import com.tangem.common.KeyPair
import com.tangem.common.card.CardWallet
import com.tangem.common.card.FirmwareVersion
import com.tangem.crypto.CryptoUtils
import com.tangem.crypto.Secp256k1
import com.tangem.crypto.generateKeyPair
import org.junit.Test

/**
 * Tests for the card-signature verification logic of [AttestWalletKeyTask].
 * Real secp256k1 key pairs and signatures are produced in-test so that the
 * cryptographic checks run end-to-end rather than against canned data.
 */
internal class AttestWalletKeyTaskTests {

    private val challenge = ByteArray(16) { 1 }
    private val publicKeySalt = ByteArray(16) { 9 }

    private val cardKeyPair: KeyPair
    private val walletKeyPair: KeyPair

    init {
        CryptoUtils.initCrypto()
        cardKeyPair = Secp256k1.generateKeyPair()
        walletKeyPair = Secp256k1.generateKeyPair()
    }

    @Test
    fun noneModeAbsentSignatureIsAccepted() {
        // The card signature was never requested, so its absence is expected and accepted.
        val isValid = makeTask(AttestWalletKeyTask.ConfirmationMode.None).verifyCardSignature(
            response = makeResponse(cardSignature = null),
            cardPublicKey = cardKeyPair.publicKey,
            firmwareVersion = FirmwareVersion.WalletOwnershipConfirmationAvailable,
            walletPublicKey = walletKeyPair.publicKey,
        )

        assertThat(isValid).isTrue()
    }

    @Test
    fun expectedSignatureAbsentFailsClosed() {
        // The signature was requested (COS 2.01+, mode != None) but the card returned none.
        // Fail closed instead of accepting an unverified response.
        listOf(AttestWalletKeyTask.ConfirmationMode.Static, AttestWalletKeyTask.ConfirmationMode.Dynamic)
            .forEach { mode ->
                val isValid = makeTask(mode).verifyCardSignature(
                    response = makeResponse(cardSignature = null),
                    cardPublicKey = cardKeyPair.publicKey,
                    firmwareVersion = FirmwareVersion.WalletOwnershipConfirmationAvailable,
                    walletPublicKey = walletKeyPair.publicKey,
                )

                assertThat(isValid).isFalse()
            }
    }

    @Test
    fun legacyFirmwareAbsentSignatureIsAccepted() {
        // On COS < 2.01 the signature is never requested (see the gate in `serialize`),
        // so its absence is expected in any confirmation mode.
        listOf(AttestWalletKeyTask.ConfirmationMode.Static, AttestWalletKeyTask.ConfirmationMode.Dynamic)
            .forEach { mode ->
                val isValid = makeTask(mode).verifyCardSignature(
                    response = makeResponse(cardSignature = null),
                    cardPublicKey = cardKeyPair.publicKey,
                    firmwareVersion = FirmwareVersion(major = 1, minor = 0),
                    walletPublicKey = walletKeyPair.publicKey,
                )

                assertThat(isValid).isTrue()
            }
    }

    @Test
    fun dynamicModeWithoutPublicKeySaltFailsClosed() {
        // Downgrade guard: dynamic was requested, but the response carries no `publicKeySalt`. Verifying
        // it against a static message would let a previously recorded static signature pass.
        val staticSignature = sign(walletKeyPair.publicKey)

        val isValid = makeTask(AttestWalletKeyTask.ConfirmationMode.Dynamic).verifyCardSignature(
            response = makeResponse(cardSignature = staticSignature),
            cardPublicKey = cardKeyPair.publicKey,
            firmwareVersion = FirmwareVersion.WalletOwnershipConfirmationAvailable,
            walletPublicKey = walletKeyPair.publicKey,
        )

        assertThat(isValid).isFalse()
    }

    @Test
    fun validDynamicSignatureIsAccepted() {
        val signature = sign(walletKeyPair.publicKey + challenge + publicKeySalt)

        val isValid = makeTask(AttestWalletKeyTask.ConfirmationMode.Dynamic).verifyCardSignature(
            response = makeResponse(cardSignature = signature, publicKeySalt = publicKeySalt),
            cardPublicKey = cardKeyPair.publicKey,
            firmwareVersion = FirmwareVersion.WalletOwnershipConfirmationAvailable,
            walletPublicKey = walletKeyPair.publicKey,
        )

        assertThat(isValid).isTrue()
    }

    @Test
    fun validStaticSignatureIsAccepted() {
        // Static mode: the signed message is just the wallet public key, with no challenge/salt binding.
        val signature = sign(walletKeyPair.publicKey)

        val isValid = makeTask(AttestWalletKeyTask.ConfirmationMode.Static).verifyCardSignature(
            response = makeResponse(cardSignature = signature),
            cardPublicKey = cardKeyPair.publicKey,
            firmwareVersion = FirmwareVersion.WalletOwnershipConfirmationAvailable,
            walletPublicKey = walletKeyPair.publicKey,
        )

        assertThat(isValid).isTrue()
    }

    @Test
    fun staticSignatureWithInjectedSaltIsRejected() {
        // A salt injected into a static response lengthens the recomputed message,
        // so the static signature no longer matches.
        val signature = sign(walletKeyPair.publicKey)

        val isValid = makeTask(AttestWalletKeyTask.ConfirmationMode.Static).verifyCardSignature(
            response = makeResponse(cardSignature = signature, publicKeySalt = publicKeySalt),
            cardPublicKey = cardKeyPair.publicKey,
            firmwareVersion = FirmwareVersion.WalletOwnershipConfirmationAvailable,
            walletPublicKey = walletKeyPair.publicKey,
        )

        assertThat(isValid).isFalse()
    }

    @Test
    fun tamperedSignatureIsRejected() {
        val tampered = sign(walletKeyPair.publicKey + challenge + publicKeySalt)
        tampered[0] = (tampered[0].toInt() xor 0xFF).toByte()

        val isValid = makeTask(AttestWalletKeyTask.ConfirmationMode.Dynamic).verifyCardSignature(
            response = makeResponse(cardSignature = tampered, publicKeySalt = publicKeySalt),
            cardPublicKey = cardKeyPair.publicKey,
            firmwareVersion = FirmwareVersion.WalletOwnershipConfirmationAvailable,
            walletPublicKey = walletKeyPair.publicKey,
        )

        assertThat(isValid).isFalse()
    }

    @Test
    fun validDynamicSignatureWithWalletStatusIsAccepted() {
        val status = CardWallet.Status.Loaded
        // The card transmits/signs walletStatus as a single byte (TAG_Status length 01), so the
        // signed message must append one byte — matching AttestWalletKeyTask.verifyCardSignature.
        val signature = sign(walletKeyPair.publicKey + challenge + publicKeySalt + byteArrayOf(status.code.toByte()))

        val isValid = makeTask(AttestWalletKeyTask.ConfirmationMode.Dynamic).verifyCardSignature(
            response = makeResponse(cardSignature = signature, publicKeySalt = publicKeySalt, walletStatus = status),
            cardPublicKey = cardKeyPair.publicKey,
            firmwareVersion = FirmwareVersion.WalletOwnershipConfirmationAvailable,
            walletPublicKey = walletKeyPair.publicKey,
        )

        assertThat(isValid).isTrue()
    }

    @Test
    fun tamperedWalletStatusIsRejected() {
        // The signature covers `Loaded`, but the response claims `Empty` — the recomputed message
        // differs, so a swapped status must fail verification.
        val signedStatus = CardWallet.Status.Loaded
        val signature =
            sign(walletKeyPair.publicKey + challenge + publicKeySalt + byteArrayOf(signedStatus.code.toByte()))

        val isValid = makeTask(AttestWalletKeyTask.ConfirmationMode.Dynamic).verifyCardSignature(
            response = makeResponse(
                cardSignature = signature,
                publicKeySalt = publicKeySalt,
                walletStatus = CardWallet.Status.Empty,
            ),
            cardPublicKey = cardKeyPair.publicKey,
            firmwareVersion = FirmwareVersion.WalletOwnershipConfirmationAvailable,
            walletPublicKey = walletKeyPair.publicKey,
        )

        assertThat(isValid).isFalse()
    }

    private fun makeTask(confirmationMode: AttestWalletKeyTask.ConfirmationMode): AttestWalletKeyTask {
        return AttestWalletKeyTask(
            publicKey = walletKeyPair.publicKey,
            challenge = challenge,
            confirmationMode = confirmationMode,
        )
    }

    private fun makeResponse(
        cardSignature: ByteArray?,
        publicKeySalt: ByteArray? = null,
        walletStatus: CardWallet.Status? = null,
    ): AttestWalletKeyResponse {
        return AttestWalletKeyResponse(
            cardId = "CB42000000005343",
            salt = ByteArray(16) { 7 },
            walletSignature = ByteArray(0),
            challenge = challenge,
            cardSignature = cardSignature,
            publicKeySalt = publicKeySalt,
            walletStatus = walletStatus,
            counter = null,
        )
    }

    private fun sign(message: ByteArray): ByteArray = Secp256k1.sign(message, cardKeyPair.privateKey)
}
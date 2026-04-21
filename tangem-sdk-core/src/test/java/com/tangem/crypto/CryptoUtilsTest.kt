package com.tangem.crypto

import com.google.common.truth.Truth.assertThat
import com.tangem.common.card.EllipticCurve
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.extensions.toHexString
import com.tangem.crypto.CryptoUtils.generatePublicKey
import com.tangem.crypto.CryptoUtils.generateRandomBytes
import com.tangem.crypto.CryptoUtils.verify
import com.tangem.crypto.CryptoUtils.verifyHash
import org.junit.Test
import java.util.Locale

internal class CryptoUtilsTest {

    init {
        CryptoUtils.initCrypto()
    }

    // region initCrypto

    @Test
    fun initCryptoSetsIsInitialized() {
        assertThat(CryptoUtils.isInitialized).isTrue()
    }

    @Test
    fun initCryptoIdempotent() {
        CryptoUtils.initCrypto()
        CryptoUtils.initCrypto()
        assertThat(CryptoUtils.isInitialized).isTrue()
    }

    // endregion

    // region generateRandomBytes

    @Test
    fun generateRandomBytesReturnsCorrectLength() {
        val bytes = generateRandomBytes(32)
        assertThat(bytes).hasLength(32)
    }

    @Test
    fun generateRandomBytesNonZero() {
        val bytes = generateRandomBytes(32)
        assertThat(bytes.sum()).isNotEqualTo(0)
    }

    @Test
    fun generateRandomBytesProducesDifferentResults() {
        val bytes1 = generateRandomBytes(32)
        val bytes2 = generateRandomBytes(32)
        assertThat(bytes1).isNotEqualTo(bytes2)
    }

    @Test
    fun generateRandomBytesVariousLengths() {
        listOf(1, 16, 32, 64, 128).forEach { length ->
            val bytes = generateRandomBytes(length)
            assertThat(bytes).hasLength(length)
        }
    }

    // endregion

    // region Sign & Verify roundtrip

    @Test
    fun verifyEd25519() {
        assertThat(signAndVerify(EllipticCurve.Ed25519)).isTrue()
    }

    @Test
    fun verifyEd25519Slip0010() {
        assertThat(signAndVerify(EllipticCurve.Ed25519Slip0010)).isTrue()
    }

    @Test
    fun verifySecp256k1() {
        assertThat(signAndVerify(EllipticCurve.Secp256k1)).isTrue()
    }

    @Test
    fun verifySecp256r1() {
        assertThat(signAndVerify(EllipticCurve.Secp256r1)).isTrue()
    }

    @Test
    fun verifyBip0340() {
        assertThat(signAndVerify(EllipticCurve.Bip0340)).isTrue()
    }

    @Test
    fun verifyFailsWithWrongPublicKey() {
        val privateKey = ByteArray(32) { 1 }
        val wrongPrivateKey = ByteArray(32) { 2 }
        val publicKey = generatePublicKey(wrongPrivateKey, EllipticCurve.Ed25519)
        val message = ByteArray(64) { 5 }
        val signature = message.sign(privateKey, EllipticCurve.Ed25519)
        assertThat(verify(publicKey, message, signature, EllipticCurve.Ed25519)).isFalse()
    }

    @Test
    fun verifyFailsWithTamperedMessage() {
        val privateKey = ByteArray(32) { 1 }
        val publicKey = generatePublicKey(privateKey, EllipticCurve.Secp256k1)
        val message = ByteArray(64) { 5 }
        val signature = message.sign(privateKey, EllipticCurve.Secp256k1)
        val tampered = message.copyOf().also { it[0] = 99.toByte() }
        assertThat(verify(publicKey, tampered, signature, EllipticCurve.Secp256k1)).isFalse()
    }

    private fun signAndVerify(curve: EllipticCurve): Boolean {
        val privateKey = ByteArray(32) { 1 }
        val publicKey = generatePublicKey(privateKey, curve)
        val message = ByteArray(64) { 5 }
        val signature = message.sign(privateKey, curve)
        return verify(publicKey, message, signature, curve)
    }

    // endregion

    // region generatePublicKey

    @Test
    fun generatePublicKeySecp256k1Uncompressed() {
        val privateKey = ByteArray(32) { 1 }
        val publicKey = generatePublicKey(privateKey, EllipticCurve.Secp256k1)
        assertThat(publicKey).isNotEmpty()
        // Uncompressed secp256k1 key starts with 0x04 and is 65 bytes
        assertThat(publicKey[0]).isEqualTo(0x04.toByte())
        assertThat(publicKey).hasLength(65)
    }

    @Test
    fun generatePublicKeySecp256k1Compressed() {
        val privateKey = ByteArray(32) { 1 }
        val publicKey = generatePublicKey(privateKey, EllipticCurve.Secp256k1, compressed = true)
        assertThat(publicKey).hasLength(33)
        // Compressed key starts with 0x02 or 0x03
        assertThat(publicKey[0].toInt()).isAnyOf(0x02, 0x03)
    }

    @Test
    fun generatePublicKeyEd25519() {
        val privateKey = ByteArray(32) { 1 }
        val publicKey = generatePublicKey(privateKey, EllipticCurve.Ed25519)
        assertThat(publicKey).hasLength(32)
    }

    @Test
    fun generatePublicKeyDeterministic() {
        val privateKey = ByteArray(32) { 1 }
        val pk1 = generatePublicKey(privateKey, EllipticCurve.Secp256k1)
        val pk2 = generatePublicKey(privateKey, EllipticCurve.Secp256k1)
        assertThat(pk1).isEqualTo(pk2)
    }

    @Test
    fun generatePublicKeyBip0340() {
        val privateKey = ByteArray(32) { 1 }
        val publicKey = generatePublicKey(privateKey, EllipticCurve.Bip0340)
        assertThat(publicKey).isNotEmpty()
        // Bip0340 (Schnorr) uses x-only public key, 32 bytes
        assertThat(publicKey).hasLength(32)
    }

    // endregion

    // region Key compression / decompression

    @Test
    fun secp256k1KeyCompression() {
        val publicKey = (
            "0432f507f6a3029028faa5913838c50f5ff3355b9b000b51889d03a2bdb96570cd750e8187482a27ca9d2dd0c" +
                "92c632155d0384521ed406753c9883621ad0da68c"
            ).hexToBytes()

        val compressedKey = Secp256k1.compressPublicKey(publicKey)
        assertThat(compressedKey.toHexString().lowercase(Locale.getDefault()))
            .isEqualTo("0232f507f6a3029028faa5913838c50f5ff3355b9b000b51889d03a2bdb96570cd")
        assertThat(compressedKey).hasLength(33)
    }

    @Test
    fun secp256k1KeyDecompression() {
        val publicKey = (
            "0432f507f6a3029028faa5913838c50f5ff3355b9b000b51889d03a2bdb96570cd750e8187482a27ca9d2dd0c" +
                "92c632155d0384521ed406753c9883621ad0da68c"
            ).hexToBytes()

        val compressedKey = Secp256k1.compressPublicKey(publicKey)
        val decompressedKey = Secp256k1.decompressPublicKey(compressedKey)
        assertThat(decompressedKey).isEqualTo(publicKey)
    }

    @Test
    fun compressPublicKeyViaCryptoUtils() {
        val publicKey = (
            "0432f507f6a3029028faa5913838c50f5ff3355b9b000b51889d03a2bdb96570cd750e8187482a27ca9d2dd0c" +
                "92c632155d0384521ed406753c9883621ad0da68c"
            ).hexToBytes()
        val compressed = CryptoUtils.compressPublicKey(publicKey, EllipticCurve.Secp256k1)
        assertThat(compressed).hasLength(33)
    }

    @Test
    fun decompressPublicKeyViaCryptoUtils() {
        val compressed = "0232f507f6a3029028faa5913838c50f5ff3355b9b000b51889d03a2bdb96570cd".hexToBytes()
        val decompressed = CryptoUtils.decompressPublicKey(compressed, EllipticCurve.Secp256k1)
        assertThat(decompressed).hasLength(65)
        assertThat(decompressed[0]).isEqualTo(0x04.toByte())
    }

    @Test
    fun compressDecompressRoundtrip() {
        val privateKey = ByteArray(32) { 7 }
        val uncompressed = generatePublicKey(privateKey, EllipticCurve.Secp256k1)
        val compressed = CryptoUtils.compressPublicKey(uncompressed)
        val restored = CryptoUtils.decompressPublicKey(compressed)
        assertThat(restored).isEqualTo(uncompressed)
    }

    // endregion

    // region isPrivateKeyValid

    @Test
    fun secp256k1PrivateKeyValidation() {
        assertThat(CryptoUtils.isPrivateKeyValid(ByteArray(0))).isFalse()
        assertThat(CryptoUtils.isPrivateKeyValid(ByteArray(32) { 0 })).isFalse()
        assertThat(CryptoUtils.isPrivateKeyValid(ByteArray(32) { 1 })).isTrue()
    }

    @Test
    fun secp256r1PrivateKeyValidation() {
        assertThat(CryptoUtils.isPrivateKeyValid(ByteArray(0), EllipticCurve.Secp256r1)).isFalse()
        assertThat(CryptoUtils.isPrivateKeyValid(ByteArray(32) { 0 }, EllipticCurve.Secp256r1)).isFalse()
        // Key equal to curve order is invalid
        assertThat(
            CryptoUtils.isPrivateKeyValid(
                "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC4FC632551".hexToBytes(),
                EllipticCurve.Secp256r1,
            ),
        ).isFalse()
        // Key just below curve order is valid
        assertThat(
            CryptoUtils.isPrivateKeyValid(
                "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632550".hexToBytes(),
                EllipticCurve.Secp256r1,
            ),
        ).isTrue()
    }

    @Test
    fun ed25519PrivateKeyValidation() {
        assertThat(CryptoUtils.isPrivateKeyValid(ByteArray(0), EllipticCurve.Ed25519)).isFalse()
        // Ed25519 hashes the private key internally, so all-zeros is a valid key
        assertThat(CryptoUtils.isPrivateKeyValid(ByteArray(32) { 0 }, EllipticCurve.Ed25519)).isTrue()
        assertThat(CryptoUtils.isPrivateKeyValid(ByteArray(32) { 1 }, EllipticCurve.Ed25519)).isTrue()
    }

    @Test
    fun bip0340PrivateKeyValidation() {
        // Bip0340 delegates to Secp256k1
        assertThat(CryptoUtils.isPrivateKeyValid(ByteArray(0), EllipticCurve.Bip0340)).isFalse()
        assertThat(CryptoUtils.isPrivateKeyValid(ByteArray(32) { 1 }, EllipticCurve.Bip0340)).isTrue()
    }

    // endregion

    // region Schnorr (Bip0340) verification with known vectors

    @Test
    fun schnorrVerifyByHash() {
        val pubKey = "208BDB9C192B5DE5DDEBA9CA8500EEC10DECB9A0980C4664F5B168F6B37EB92A".hexToBytes()
        val hash = "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes()
        val signature = (
            "735951D8481B99777AB0ABADEFDA903E485756DE3599E75AF655B7F26CB7634956DEDEB89DB3E40A7B9ED095E5" +
                "855290F8EB85C22E57A001A4A64385AB11A5B3"
            ).hexToBytes()
        assertThat(verifyHash(pubKey, hash, signature, EllipticCurve.Bip0340)).isTrue()
    }

    @Test
    fun schnorrVerifyByMessage() {
        val pubKey = "DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659".hexToBytes()
        val message = "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89".hexToBytes()
        val signature = (
            "0560D3B34117AAE83F028AB92B3F8C16E3FDA34D55D3C7A2E1F2EDFE0A0071491D8D2302A6810FC017EE4CBF6B" +
                "F13ADF36F9C0967FFCFE1A64BCBBDA73CA813B"
            ).hexToBytes()
        assertThat(verify(pubKey, message, signature, EllipticCurve.Bip0340)).isTrue()
    }

    // endregion

    // region encrypt / decrypt

    @Test
    fun encryptDecryptRoundtrip() {
        val key = generateRandomBytes(32)
        val plaintext = "Hello, Tangem!".toByteArray()
        val encrypted = plaintext.encrypt(key)
        val decrypted = encrypted.decrypt(key)
        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun encryptDecryptNoPaddingRoundtrip() {
        val key = generateRandomBytes(32)
        // NoPadding requires data to be a multiple of 16 bytes
        val plaintext = ByteArray(32) { it.toByte() }
        val encrypted = plaintext.encrypt(key, usePkcs7 = false)
        val decrypted = encrypted.decrypt(key, usePkcs7 = false)
        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun encryptProducesDifferentOutput() {
        val key = generateRandomBytes(32)
        val plaintext = "test data".toByteArray()
        val encrypted = plaintext.encrypt(key)
        assertThat(encrypted).isNotEqualTo(plaintext)
    }

    // endregion

    // region AES-CCM encrypt / decrypt

    @Test
    fun aesCcmEncryptDecryptRoundtrip() {
        val key = generateRandomBytes(32)
        val nonce = generateRandomBytes(12)
        val aad = "additional data".toByteArray()
        val plaintext = "Secret message".toByteArray()

        val encrypted = plaintext.encryptAesCcm(key, nonce, aad)
        val decrypted = encrypted.decryptAesCcm(key, nonce, aad)
        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun aesCcmEncryptProducesCiphertext() {
        val key = generateRandomBytes(32)
        val nonce = generateRandomBytes(12)
        val aad = ByteArray(0)
        val plaintext = "test".toByteArray()

        val encrypted = plaintext.encryptAesCcm(key, nonce, aad)
        assertThat(encrypted).isNotEqualTo(plaintext)
        // CCM appends an auth tag, so ciphertext should be longer
        assertThat(encrypted.size).isGreaterThan(plaintext.size)
    }

    // endregion

    // region secureCompare

    @Test
    fun secureCompareEqualArrays() {
        val a = byteArrayOf(1, 2, 3, 4, 5)
        val b = byteArrayOf(1, 2, 3, 4, 5)
        assertThat(a.secureCompare(b)).isTrue()
    }

    @Test
    fun secureCompareDifferentArrays() {
        val a = byteArrayOf(1, 2, 3, 4, 5)
        val b = byteArrayOf(1, 2, 3, 4, 6)
        assertThat(a.secureCompare(b)).isFalse()
    }

    @Test
    fun secureCompareDifferentLengths() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(1, 2, 3, 4)
        assertThat(a.secureCompare(b)).isFalse()
    }

    @Test
    fun secureCompareEmptyArrays() {
        assertThat(ByteArray(0).secureCompare(ByteArray(0))).isTrue()
    }

    // endregion

    // region hmacSha512

    @Test
    fun hmacSha512ProducesCorrectLength() {
        val key = "secret".toByteArray()
        val data = "message".toByteArray()
        val mac = key.hmacSha512(data)
        assertThat(mac).hasLength(64)
    }

    @Test
    fun hmacSha512Deterministic() {
        val key = "secret".toByteArray()
        val data = "message".toByteArray()
        assertThat(key.hmacSha512(data)).isEqualTo(key.hmacSha512(data))
    }

    @Test
    fun hmacSha512DifferentKeysProduceDifferentResults() {
        val data = "message".toByteArray()
        val mac1 = "key1".toByteArray().hmacSha512(data)
        val mac2 = "key2".toByteArray().hmacSha512(data)
        assertThat(mac1).isNotEqualTo(mac2)
    }

    // endregion

    // region hmacSha256

    @Test
    fun hmacSha256ProducesCorrectLength() {
        val key = "secret".toByteArray()
        val data = "message".toByteArray()
        val mac = key.hmacSha256(data)
        assertThat(mac).hasLength(32)
    }

    @Test
    fun hmacSha256Deterministic() {
        val key = "secret".toByteArray()
        val data = "message".toByteArray()
        assertThat(key.hmacSha256(data)).isEqualTo(key.hmacSha256(data))
    }

    // endregion

    // region xorWith

    @Test
    fun xorWithProducesCorrectResult() {
        val a = byteArrayOf(0x0F, 0xF0.toByte(), 0xAA.toByte())
        val b = byteArrayOf(0xF0.toByte(), 0x0F, 0x55)
        val result = a.xorWith(b)
        assertThat(result).isEqualTo(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
    }

    @Test
    fun xorWithZeroIsIdentity() {
        val a = byteArrayOf(1, 2, 3, 4)
        val zeros = ByteArray(4)
        assertThat(a.xorWith(zeros)).isEqualTo(a)
    }

    @Test
    fun xorWithSelfIsZero() {
        val a = byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte())
        val result = a.xorWith(a)
        assertThat(result).isEqualTo(ByteArray(3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun xorWithDifferentLengthsThrows() {
        byteArrayOf(1, 2).xorWith(byteArrayOf(1, 2, 3))
    }

    // endregion

    // region normalize

    @Test
    fun normalizeSecp256k1Signature() {
        val privateKey = ByteArray(32) { 1 }
        val message = ByteArray(32) { 5 }
        val signature = message.sign(privateKey, EllipticCurve.Secp256k1)
        val normalized = CryptoUtils.normalize(signature)
        // Normalized signature should still verify
        val publicKey = generatePublicKey(privateKey, EllipticCurve.Secp256k1)
        assertThat(verify(publicKey, message, normalized, EllipticCurve.Secp256k1)).isTrue()
    }

    // endregion

    // region loadPublicKey

    @Test
    fun loadPublicKeySecp256k1() {
        val privateKey = ByteArray(32) { 1 }
        val publicKeyBytes = generatePublicKey(privateKey, EllipticCurve.Secp256k1)
        val publicKey = CryptoUtils.loadPublicKey(publicKeyBytes, EllipticCurve.Secp256k1)
        assertThat(publicKey).isNotNull()
    }

    @Test
    fun loadPublicKeyEd25519() {
        val privateKey = ByteArray(32) { 1 }
        val publicKeyBytes = generatePublicKey(privateKey, EllipticCurve.Ed25519)
        val publicKey = CryptoUtils.loadPublicKey(publicKeyBytes, EllipticCurve.Ed25519)
        assertThat(publicKey).isNotNull()
    }

    @Test(expected = UnsupportedOperationException::class)
    fun loadPublicKeyBlsThrows() {
        val privateKey = ByteArray(32) { 1 }
        val publicKeyBytes = generatePublicKey(privateKey, EllipticCurve.Bls12381G2)
        CryptoUtils.loadPublicKey(publicKeyBytes, EllipticCurve.Bls12381G2)
    }

    // endregion

    // region generateKeyPair extension

    @Test
    fun generateKeyPairSecp256k1() {
        val keyPair = Secp256k1.generateKeyPair()
        assertThat(keyPair.publicKey).isNotEmpty()
        assertThat(keyPair.privateKey).hasLength(32)
        // Verify the key pair is valid by sign+verify
        val message = ByteArray(32) { 3 }
        val signature = message.sign(keyPair.privateKey, EllipticCurve.Secp256k1)
        assertThat(verify(keyPair.publicKey, message, signature, EllipticCurve.Secp256k1)).isTrue()
    }

    // endregion
}
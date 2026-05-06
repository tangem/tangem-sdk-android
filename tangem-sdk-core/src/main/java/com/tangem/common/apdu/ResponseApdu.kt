package com.tangem.common.apdu

import com.tangem.common.core.TangemSdkError
import com.tangem.common.encryption.EncryptionMode
import com.tangem.common.extensions.calculateCrc16
import com.tangem.common.tlv.Tlv
import com.tangem.crypto.decrypt
import com.tangem.crypto.decryptAesCcm
import java.io.ByteArrayInputStream

/**
 * Stores response data from the card and parses it to [Tlv] and [StatusWord].
 *
 * @property data Response payload (without status word bytes).
 * @property sw Status word code, reflecting the status of the response.
 * @property statusWord Parsed status word.
 */
@Suppress("MagicNumber")
class ResponseApdu(val data: ByteArray, val sw1: Int, val sw2: Int) {

    val sw: Int = sw1 shl 8 or sw2

    val statusWord: StatusWord = StatusWord.byCode(sw)

    val swBytes: ByteArray get() = byteArrayOf(sw1.toByte(), sw2.toByte())

    /**
     * Converts raw response data to the list of TLVs.
     */
    fun getTlvData(): List<Tlv>? {
        return Tlv.deserialize(data)
    }

    /**
     * Decrypts the response APDU data.
     * @param encryptionMode encryption mode
     * @param encryptionKey The key used for decryption. If null, returns the original APDU.
     * @param nonce The nonce (initialization vector) used for CCM decryption.
     * @return A new [ResponseApdu] with decrypted payload data.
     */
    fun decrypt(encryptionMode: EncryptionMode, encryptionKey: ByteArray?, nonce: ByteArray? = null): ResponseApdu {
        if (encryptionKey == null) return this

        return when (encryptionMode) {
            EncryptionMode.None, EncryptionMode.Fast, EncryptionMode.Strong -> {
                decryptLegacy(encryptionKey)
            }
            EncryptionMode.CcmWithSecurityDelay,
            EncryptionMode.CcmWithAccessToken,
            EncryptionMode.CcmWithAsymmetricKeys,
            -> {
                val ccmNonce = nonce ?: throw TangemSdkError.FailedToDecryptApdu()
                decryptCcm(encryptionKey, ccmNonce)
            }
        }
    }

    /**
     * Decrypts the response APDU data using AES-CBC encryption.
     * @param encryptionKey The key used for decryption.
     * @return A new [ResponseApdu] with decrypted payload data.
     */
    @Suppress("MagicNumber")
    private fun decryptLegacy(encryptionKey: ByteArray): ResponseApdu {
        if (data.isEmpty()) return this // error response, nothing to decrypt

        if (data.size < 16) return this // not encrypted response, nothing to decrypt

        val decryptedData: ByteArray = data.decrypt(encryptionKey)

        if (decryptedData.size < 4) throw TangemSdkError.InvalidResponse()

        val inputStream = ByteArrayInputStream(decryptedData)
        val baLength = ByteArray(2)
        inputStream.read(baLength)
        val length = (baLength[0].toInt() and 0xFF) * 256 + (baLength[1].toInt() and 0xFF)
        val baCRC = ByteArray(2)
        inputStream.read(baCRC)
        val answerData = ByteArray(length)
        inputStream.read(answerData)

        if (length != decryptedData.size - 4) throw TangemSdkError.InvalidResponse()

        val crc: ByteArray = answerData.calculateCrc16()
        if (!baCRC.contentEquals(crc)) throw TangemSdkError.InvalidResponse()

        return ResponseApdu(answerData, sw1, sw2)
    }

    /**
     * Decrypts the response APDU data using AES-CCM encryption.
     * Nonce is extracted from the beginning of the response data.
     * @param encryptionKey The key used for decryption.
     * @param nonceLength Length of the CCM nonce prepended to the encrypted payload.
     * @return A new [ResponseApdu] with decrypted payload data.
     */
    @Suppress("MagicNumber")
    fun decryptCcm(encryptionKey: ByteArray, nonceLength: Int): ResponseApdu {
        if (data.isEmpty()) return this // error response, nothing to decrypt

        if (data.size < nonceLength) throw TangemSdkError.InvalidResponse()

        val nonce = data.copyOfRange(0, nonceLength)
        val encryptedData = data.copyOfRange(nonceLength, data.size)

        val trimmedApdu = ResponseApdu(encryptedData, sw1, sw2)
        return trimmedApdu.decryptCcm(encryptionKey, nonce)
    }

    /**
     * Decrypts the response APDU data using AES-CCM encryption.
     * @param encryptionKey The key used for decryption.
     * @param nonce The nonce (initialization vector) used for CCM decryption.
     * @return A new [ResponseApdu] with decrypted payload data.
     */
    @Suppress("MagicNumber")
    private fun decryptCcm(encryptionKey: ByteArray, nonce: ByteArray): ResponseApdu {
        if (data.isEmpty()) return this // error response, nothing to decrypt

        // always unencrypted
        if (statusWord == StatusWord.NeedPause || statusWord == StatusWord.NeedEncryption) return this

        // minimum length of AES-CCM encrypted data is 8 bytes (authentication tag)
        if (data.size < 8) throw TangemSdkError.InvalidResponse()

        val decryptedPayload = data.decryptAesCcm(
            key = encryptionKey,
            nonce = nonce,
            associatedData = swBytes,
        )

        return ResponseApdu(decryptedPayload, sw1, sw2)
    }

    override fun toString(): String {
        return "<<<< [${data.size + 2} bytes]: $sw1 $sw2 (SW: $statusWord)"
    }

    companion object {
        fun fromRawBytes(rawData: ByteArray): ResponseApdu {
            val sw1 = 0x00FF and rawData[rawData.size - 2].toInt()
            val sw2 = 0x00FF and rawData[rawData.size - 1].toInt()
            val data = rawData.copyOf(rawData.size - 2)
            return ResponseApdu(data, sw1, sw2)
        }
    }
}
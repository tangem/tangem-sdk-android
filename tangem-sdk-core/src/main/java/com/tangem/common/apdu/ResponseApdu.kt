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
 * @property data Raw response from the card.
 * @property sw Status word code, reflecting the status of the response.
 * @property statusWord Parsed status word.
 */
class ResponseApdu(private val data: ByteArray) {

    private val sw1: Int = 0x00FF and data[data.size - 2].toInt()
    private val sw2: Int = 0x00FF and data[data.size - 1].toInt()

    val sw: Int = sw1 shl 8 or sw2

    val statusWord: StatusWord = StatusWord.byCode(sw)

    private val swBytes: ByteArray get() = byteArrayOf(data[data.size - 2], data[data.size - 1])

    /**
     * Response data without status word bytes.
     */
    private val responseData: ByteArray get() = data.copyOf(data.size - 2)

    /**
     * Converts raw response data to the list of TLVs.
     */
    fun getTlvData(): List<Tlv>? {
        return if (data.size <= 2) {
            null
        } else {
            Tlv.deserialize(data.copyOf(data.size - 2))
        }
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
        val responseData = responseData

        if (responseData.isEmpty()) return this // error response, nothing to decrypt

        if (responseData.size < 16) return this // not encrypted response, nothing to decrypt

        val decryptedData: ByteArray = responseData.decrypt(encryptionKey)

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

        return ResponseApdu(answerData + data[data.size - 2] + data[data.size - 1])
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
        val responseData = responseData

        if (responseData.isEmpty()) return this // error response, nothing to decrypt

        if (responseData.size < nonceLength) throw TangemSdkError.InvalidResponse()

        val nonce = responseData.copyOfRange(0, nonceLength)
        val encryptedData = responseData.copyOfRange(nonceLength, responseData.size)

        val trimmedApdu = ResponseApdu(encryptedData + swBytes)
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
        val responseData = responseData

        if (responseData.isEmpty()) return this // error response, nothing to decrypt

        // always unencrypted
        if (statusWord == StatusWord.NeedPause || statusWord == StatusWord.NeedEncryption) return this

        // minimum length of AES-CCM encrypted data is 8 bytes (authentication tag)
        if (responseData.size < 8) throw TangemSdkError.InvalidResponse()

        val decryptedPayload = responseData.decryptAesCcm(
            key = encryptionKey,
            nonce = nonce,
            associatedData = swBytes,
        )

        return ResponseApdu(decryptedPayload + data[data.size - 2] + data[data.size - 1])
    }

    override fun toString(): String {
        return "<<<< [${data.size} bytes]: $sw1 $sw2 (SW: $statusWord)"
    }
}
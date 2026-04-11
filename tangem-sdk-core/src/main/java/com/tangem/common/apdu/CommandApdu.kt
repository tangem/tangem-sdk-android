package com.tangem.common.apdu

import com.tangem.Log
import com.tangem.common.core.TangemSdkError
import com.tangem.common.encryption.AesMode
import com.tangem.common.encryption.EncryptionMode
import com.tangem.common.extensions.calculateCrc16
import com.tangem.common.extensions.toByteArray
import com.tangem.common.extensions.toHexString
import com.tangem.crypto.encrypt
import com.tangem.crypto.encryptAesCcm
import java.io.ByteArrayOutputStream

/**
 * Class that provides conversion of serialized request and Instruction code
 * to a raw data that can be sent to the card.
 *
 * @property ins Instruction code that determines the type of request for the card.
 * @property tlvs Tlvs encoded to a [ByteArray] that are to be sent to the card.
 */
class CommandApdu(

    val ins: Int,
    private val tlvs: ByteArray,

    private val p1: Int,
    private val p2: Int,

    private val le: Int? = null,

    private val cla: Int = ISO_CLA,
) {

    /**
     * Request converted to a raw data
     */
    val apduData: ByteArray

    init {
        apduData = serialize()
    }

    constructor(instruction: Instruction, tlvs: ByteArray) : this(instruction.code, tlvs, 0, 0)

    /**
     * Serialize as an extended APDU.
     * @return Data to send
     */
    fun serialize(): ByteArray {
        val data = tlvs

        val byteStream = ByteArrayOutputStream()
        byteStream.write(cla)
        byteStream.write(ins)
        byteStream.write(p1)
        byteStream.write(p2)
        if (data.isNotEmpty()) {
            // append LC as an extended field
            byteStream.write(0)
            byteStream.write(data.size.shr(8))
            byteStream.write(data.size.and(0xFF))
            byteStream.write(data)
        }
        if (le != null) {
            // append LE as an extended field
            byteStream.write(le.shr(bitCount = 8))
            byteStream.write(le.and(other = 0xFF))
        }
        return byteStream.toByteArray()
    }

    private fun ByteArrayOutputStream.writeLength(lc: Int) {
        this.write(0)
        this.write(lc.shr(bitCount = 8))
        this.write(lc.and(other = 0xFF))
    }

    /**
     * Encrypt APDU.
     *
     * @param encryptionMode encryption mode
     * @param encryptionKey encryption key
     * @param nonce nonce for CCM encryption (required for CCM modes)
     * @return Encrypted APDU
     */
    fun encrypt(encryptionMode: EncryptionMode, encryptionKey: ByteArray?, nonce: ByteArray? = null): CommandApdu {
        if (encryptionKey == null || p1 != EncryptionMode.None.byteValue) {
            // skip if already encrypted or empty encryptionKey
            return this
        }

        return when (encryptionMode) {
            EncryptionMode.None, EncryptionMode.Fast, EncryptionMode.Strong -> {
                encryptLegacy(encryptionKey, encryptionMode.byteValue)
            }
            EncryptionMode.CcmWithSecurityDelay, EncryptionMode.CcmWithAccessToken, EncryptionMode.CcmWithAsymmetricKeys -> {
                val ccmNonce = nonce ?: throw TangemSdkError.FailedToEncryptApdu()
                encryptCcm(encryptionKey, ccmNonce)
            }
        }
    }

    private fun encryptLegacy(encryptionKey: ByteArray, newP1: Int): CommandApdu {
        val crc: ByteArray = tlvs.calculateCrc16()
        val dataToEncrypt = tlvs.size.toByteArray(2) + crc + tlvs
        val encryptedData = dataToEncrypt.encrypt(encryptionKey)

        Log.apdu { "C-APDU encrypted" }

        return CommandApdu(ins, encryptedData, newP1, p2, le, cla)
    }

    private fun encryptCcm(encryptionKey: ByteArray, nonce: ByteArray): CommandApdu {
        val newP1 = AesMode.Ccm.p1
        val associatedData = byteArrayOf(cla.toByte(), ins.toByte(), newP1.toByte(), p2.toByte())

        val encryptedPayload = tlvs.encryptAesCcm(
            key = encryptionKey,
            nonce = nonce,
            associatedData = associatedData,
        )

        Log.apdu { "C-APDU encrypted with CCM" }

        return CommandApdu(ins, encryptedPayload, newP1, p2, le, cla)
    }

    override fun toString(): String {
        val instruction = Instruction.byCode(ins)
        val bytes = serialize()
        return "$instruction [${bytes.size} bytes]: ${bytes.toHexString()}"
    }

    companion object {
        const val ISO_CLA = 0x00
    }
}
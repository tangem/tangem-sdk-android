package com.tangem.common.v8

import com.tangem.common.core.AccessLevel
import com.tangem.common.core.CardSessionEncryption
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.extensions.to3Bytes

/**
 * Encapsulates all CCM encryption state for v8+ secure channel protocol.
 */
@Suppress("MagicNumber")
class SecureChannelSession(cardId: String) {

    var accessLevel: AccessLevel = AccessLevel.Public
        private set

    var isAuthorizedWithAccessCode: Boolean = false
        private set

    var packetCounter: Int = 0
        private set

    private val cardIdBytes: ByteArray = cardId.hexToBytes()

    /**
     * Constructs a 12-byte nonce for AES-CCM encryption.
     * Format: [prefix(1)] + [cardId bytes(8)] + [packetCounter big-endian(3)] = 12 bytes
     */
    fun makeCommandAPDUNonce(): ByteArray {
        val prefix = 0x7E.toByte()
        val counterBytes = packetCounter.to3Bytes()
        return byteArrayOf(prefix) + cardIdBytes + counterBytes
    }

    /**
     * Constructs a 12-byte nonce for AES-CCM encryption.
     * Format: [prefix(1)] + [cardId bytes(8)] + [packetCounter big-endian(3)] = 12 bytes
     */
    fun makeResponseAPDUNonce(): ByteArray {
        val prefix = 0xCA.toByte()
        val counterBytes = packetCounter.to3Bytes()
        return byteArrayOf(prefix) + cardIdBytes + counterBytes
    }

    fun incrementPacketCounter() {
        packetCounter += 1
    }

    fun isElevationRequired(encryption: CardSessionEncryption): Boolean {
        return when (encryption) {
            CardSessionEncryption.NONE -> false
            CardSessionEncryption.PUBLIC_SECURE_CHANNEL -> accessLevel.isPublic()
            CardSessionEncryption.SECURE_CHANNEL -> accessLevel.isPublic() || accessLevel.isPublicSecureChannel()
            CardSessionEncryption.SECURE_CHANNEL_WITH_PIN ->
                accessLevel.isPublic() || accessLevel.isPublicSecureChannel() || !isAuthorizedWithAccessCode
        }
    }

    fun didEstablishChannel(accessLevel: AccessLevel) {
        this.accessLevel = accessLevel
        packetCounter = 1
    }

    fun didAuthorizePin(accessLevel: AccessLevel) {
        this.accessLevel = accessLevel
        isAuthorizedWithAccessCode = true
    }

    fun reset() {
        accessLevel = AccessLevel.Public
        isAuthorizedWithAccessCode = false
        packetCounter = 0
    }
}
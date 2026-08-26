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

    /**
     * Whether the User level was reached via PIN challenge/response. The card distinguishes HOW
     * User was reached: PIN-level commands (e.g. SET_PIN) are rejected with AccessDenied over a
     * token-established User channel — intentional firmware behavior, changing the code requires
     * proving knowledge of the current one.
     */
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
        if (accessLevel.isPublic()) {
            // Do not increment the counter until the secure channel is established
            return
        }

        packetCounter += 1
    }

    fun isElevationRequired(encryption: CardSessionEncryption): Boolean {
        return when (encryption) {
            CardSessionEncryption.NONE -> false
            CardSessionEncryption.PUBLIC_SECURE_CHANNEL -> accessLevel.isPublic()
            CardSessionEncryption.SECURE_CHANNEL -> accessLevel.isPublic() || accessLevel.isPublicSecureChannel()
            // The card accepts PIN-level commands only after an explicit PIN authorization:
            // a token-established User channel is not enough (see isAuthorizedWithAccessCode)
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
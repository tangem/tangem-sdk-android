package com.tangem.operations.securechannel.establish

import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvTag
import com.tangem.operations.Command
import com.tangem.operations.CommandResponse
import com.tangem.operations.PreflightReadMode

data class AuthorizeWithSecurityDelayResponse(
    val pubSessionKeyA: ByteArray,
) : CommandResponse {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AuthorizeWithSecurityDelayResponse
        return pubSessionKeyA.contentEquals(other.pubSessionKeyA)
    }

    override fun hashCode(): Int = pubSessionKeyA.contentHashCode()
}

/**
 * Initiates authorization with security delay for v8+ secure channel establishment.
 * Uses the Authorize instruction to begin the ECDH key exchange process.
 */
class AuthorizeWithSecurityDelayCommand : Command<AuthorizeWithSecurityDelayResponse>() {

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.None

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = createTlvBuilder(legacyMode = environment.legacyMode)
        // TODO: Add required TLV fields for security delay authorization
        return CommandApdu(instruction = Instruction.Authorize, tlvs = tlvBuilder.serialize())
    }

    override fun deserialize(
        environment: SessionEnvironment,
        apdu: ResponseApdu,
    ): AuthorizeWithSecurityDelayResponse {
        val decoder = createTlvDecoder(environment, apdu)
        return AuthorizeWithSecurityDelayResponse(
            pubSessionKeyA = decoder.decode(TlvTag.SessionKeyA),
        )
    }
}
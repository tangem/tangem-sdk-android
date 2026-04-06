package com.tangem.operations.securechannel.authorize

import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvTag
import com.tangem.operations.Command
import com.tangem.operations.CommandResponse
import com.tangem.operations.PreflightReadMode

data class AuthorizeWithPinChallengeResponse(
    val challengeWithXor: ByteArray,
) : CommandResponse {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AuthorizeWithPinChallengeResponse
        return challengeWithXor.contentEquals(other.challengeWithXor)
    }

    override fun hashCode(): Int = challengeWithXor.contentHashCode()
}

/**
 * Sends a PIN challenge request to the card for v8+ PIN authorization.
 * Returns a challenge that is used with the access code HMAC.
 */
class AuthorizeWithPinChallengeCommand : Command<AuthorizeWithPinChallengeResponse>() {

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.None

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = createTlvBuilder(legacyMode = environment.legacyMode)
        // TODO: Add required TLV fields for PIN challenge
        return CommandApdu(instruction = Instruction.Authorize, tlvs = tlvBuilder.serialize())
    }

    override fun deserialize(
        environment: SessionEnvironment,
        apdu: ResponseApdu,
    ): AuthorizeWithPinChallengeResponse {
        val decoder = createTlvDecoder(environment, apdu)
        return AuthorizeWithPinChallengeResponse(
            challengeWithXor = decoder.decode(TlvTag.Challenge),
        )
    }
}
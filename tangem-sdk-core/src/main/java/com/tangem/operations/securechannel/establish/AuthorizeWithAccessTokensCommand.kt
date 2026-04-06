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

data class AuthorizeWithAccessTokenResponse(
    val challengeA: ByteArray,
) : CommandResponse {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AuthorizeWithAccessTokenResponse
        return challengeA.contentEquals(other.challengeA)
    }

    override fun hashCode(): Int = challengeA.contentHashCode()
}

/**
 * Initiates authorization with access tokens for v8+ secure channel establishment.
 * Returns a challenge from the card that is used for the HMAC attestation.
 */
class AuthorizeWithAccessTokensCommand : Command<AuthorizeWithAccessTokenResponse>() {

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.None

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = createTlvBuilder(legacyMode = environment.legacyMode)
        // TODO: Add required TLV fields for access token authorization
        return CommandApdu(instruction = Instruction.Authorize, tlvs = tlvBuilder.serialize())
    }

    override fun deserialize(
        environment: SessionEnvironment,
        apdu: ResponseApdu,
    ): AuthorizeWithAccessTokenResponse {
        val decoder = createTlvDecoder(environment, apdu)
        return AuthorizeWithAccessTokenResponse(
            challengeA = decoder.decode(TlvTag.Challenge),
        )
    }
}
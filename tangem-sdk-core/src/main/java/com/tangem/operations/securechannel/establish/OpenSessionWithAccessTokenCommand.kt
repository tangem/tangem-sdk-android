package com.tangem.operations.securechannel.establish

import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.core.AccessLevel
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvTag
import com.tangem.operations.Command
import com.tangem.operations.CommandResponse
import com.tangem.operations.PreflightReadMode

data class OpenSessionWithAccessTokenResponse(
    val accessLevel: AccessLevel,
) : CommandResponse

/**
 * Completes the secure channel establishment with access tokens.
 * Sends HMAC attestation and session key to the card.
 */
class OpenSessionWithAccessTokenCommand(
    private val challengeB: ByteArray,
    private val hmacAttestB: ByteArray,
    private val sessionKey: ByteArray,
) : Command<OpenSessionWithAccessTokenResponse>() {

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.None

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = createTlvBuilder(legacyMode = environment.legacyMode)
        tlvBuilder.append(TlvTag.Challenge, challengeB)
        // TODO: Add hmacAttestB and sessionKey TLV fields
        return CommandApdu(instruction = Instruction.OpenSession, tlvs = tlvBuilder.serialize())
    }

    override fun deserialize(
        environment: SessionEnvironment,
        apdu: ResponseApdu,
    ): OpenSessionWithAccessTokenResponse {
        val decoder = createTlvDecoder(environment, apdu)
        return OpenSessionWithAccessTokenResponse(
            accessLevel = decoder.decode(TlvTag.AccessLevel),
        )
    }
}
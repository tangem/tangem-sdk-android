package com.tangem.operations.securechannel.authorize

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

data class AuthorizeWithPinResponse(
    val accessLevel: AccessLevel,
) : CommandResponse

/**
 * Sends the HMAC response for PIN authorization to the card.
 * Completes the PIN challenge-response flow and elevates the session access level.
 */
class AuthorizeWithPinResponseCommand(
    private val challengeWithXor: ByteArray,
) : Command<AuthorizeWithPinResponse>() {

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.None

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = createTlvBuilder(legacyMode = environment.legacyMode)
        tlvBuilder.append(TlvTag.Challenge, challengeWithXor)
        // TODO: Add HMAC of access code
        return CommandApdu(instruction = Instruction.Authorize, tlvs = tlvBuilder.serialize())
    }

    override fun deserialize(
        environment: SessionEnvironment,
        apdu: ResponseApdu,
    ): AuthorizeWithPinResponse {
        val decoder = createTlvDecoder(environment, apdu)
        return AuthorizeWithPinResponse(
            accessLevel = decoder.decode(TlvTag.AccessLevel),
        )
    }
}
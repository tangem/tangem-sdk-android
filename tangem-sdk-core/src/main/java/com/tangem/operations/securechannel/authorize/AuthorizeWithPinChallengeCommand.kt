package com.tangem.operations.securechannel.authorize

import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.CardSessionEncryption
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.core.TangemSdkError
import com.tangem.common.tlv.TlvTag
import com.tangem.operations.Command
import com.tangem.operations.CommandResponse
import com.tangem.operations.PreflightReadMode
import com.tangem.operations.resetcode.AuthorizeMode

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
 * Requests a challenge from the card for PIN verification.
 */
class AuthorizeWithPinChallengeCommand : Command<AuthorizeWithPinChallengeResponse>() {

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.None

    override val cardSessionEncryption: CardSessionEncryption = CardSessionEncryption.PUBLIC_SECURE_CHANNEL

    override fun performPreCheck(card: Card): TangemSdkError? {
        if (card.firmwareVersion < FirmwareVersion.v8) {
            return TangemSdkError.NotSupportedFirmwareVersion()
        }
        return null
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = createTlvBuilder(legacyMode = environment.legacyMode)
        tlvBuilder.append(TlvTag.InteractionMode, AuthorizeMode.PinChallenge)
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
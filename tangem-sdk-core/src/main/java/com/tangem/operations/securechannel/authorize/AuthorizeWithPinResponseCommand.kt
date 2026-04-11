package com.tangem.operations.securechannel.authorize

import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.AccessLevel
import com.tangem.common.core.CardSessionEncryption
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.core.TangemSdkError
import com.tangem.common.tlv.TlvTag
import com.tangem.crypto.hmacSha256
import com.tangem.crypto.xorWith
import com.tangem.operations.Command
import com.tangem.operations.CommandResponse
import com.tangem.operations.PreflightReadMode
import com.tangem.operations.resetcode.AuthorizeMode

data class AuthorizeWithPinResponse(
    val accessLevel: AccessLevel,
) : CommandResponse

/**
 * Sends the HMAC-based PIN response to the card for verification.
 * Completes the PIN challenge-response flow and elevates the session access level.
 */
class AuthorizeWithPinResponseCommand(
    private val challengeWithXor: ByteArray,
) : Command<AuthorizeWithPinResponse>() {

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.None

    override val cardSessionEncryption: CardSessionEncryption = CardSessionEncryption.PUBLIC_SECURE_CHANNEL

    override fun performPreCheck(card: Card): TangemSdkError? {
        if (card.firmwareVersion < FirmwareVersion.v8) {
            return TangemSdkError.NotSupportedFirmwareVersion()
        }
        return null
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val pin = environment.accessCode.value
            ?: throw TangemSdkError.SerializeCommandError()

        val pinChallenge = challengeWithXor.xorWith(pin)
        val hmacPin = challengeWithXor.hmacSha256("PIN".toByteArray() + pinChallenge)

        val tlvBuilder = createTlvBuilder(legacyMode = environment.legacyMode)
        tlvBuilder.append(TlvTag.InteractionMode, AuthorizeMode.PinResponse)
        tlvBuilder.append(TlvTag.Pin, hmacPin)
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
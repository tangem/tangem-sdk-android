package com.tangem.operations

import com.squareup.moshi.JsonClass
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.Card
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.core.TangemSdkError
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag
import com.tangem.crypto.hmacSha256
import com.tangem.operations.resetcode.AuthorizeMode

@JsonClass(generateAdapter = true)
class AuthorizeWithPinChallengeResponse(
    val challenge: ByteArray,
) : CommandResponse

@JsonClass(generateAdapter = true)
class AuthorizeWithPinResponseResponse(
    val accessLevel: Card.AccessLevel,
) : CommandResponse


/**
 * In case of encrypted communication, App should setup a session before calling any further command.
 */
class AuthorizeWithPinChallengeCommand(
) : Command<AuthorizeWithPinChallengeResponse> (){

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = TlvBuilder()
        tlvBuilder.append(TlvTag.InteractionMode, AuthorizeMode.AuthorizeWithPIN_Challenge)

        return CommandApdu(Instruction.Authorize.code, tlvBuilder.serialize(), 0, 0)
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): AuthorizeWithPinChallengeResponse {
        val tlvData = apdu.getTlvData() ?: throw TangemSdkError.DeserializeApduFailed()

        val decoder = TlvDecoder(tlvData)
        return AuthorizeWithPinChallengeResponse(decoder.decode(TlvTag.Challenge))
    }
}

class AuthorizeWithPinResponseCommand(val challenge: ByteArray
) : Command<AuthorizeWithPinResponseResponse> (){

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = TlvBuilder()
        tlvBuilder.append(TlvTag.InteractionMode, AuthorizeMode.AuthorizeWithPIN_Response)
        val pin=environment.accessCode.value?: ByteArray(0)
        val hmacPIN = pin.hmacSha256("PIN".toByteArray() + challenge)

        tlvBuilder.append(TlvTag.Pin, hmacPIN)

        return CommandApdu(Instruction.Authorize.code, tlvBuilder.serialize(), 0, 0)
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): AuthorizeWithPinResponseResponse {
        val tlvData = apdu.getTlvData() ?: throw TangemSdkError.DeserializeApduFailed()

        val decoder = TlvDecoder(tlvData)
        return AuthorizeWithPinResponseResponse(decoder.decode(TlvTag.AccessLevel))
    }
}
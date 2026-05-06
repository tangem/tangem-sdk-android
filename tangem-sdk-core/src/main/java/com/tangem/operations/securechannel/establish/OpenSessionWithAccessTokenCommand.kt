package com.tangem.operations.securechannel.establish

import com.tangem.Log
import com.tangem.common.CompletionResult
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.AccessLevel
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionEncryption
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.core.TangemSdkError
import com.tangem.common.encryption.EncryptionMode
import com.tangem.common.tlv.TlvTag
import com.tangem.crypto.CryptoUtils
import com.tangem.operations.Command
import com.tangem.operations.CommandResponse
import com.tangem.operations.PreflightReadMode

data class OpenSessionWithAccessTokenResponse(
    val accessLevel: AccessLevel,
    val signAttestSession: ByteArray,
) : CommandResponse {

    fun verify(sessionKey: ByteArray, cardPublicKey: ByteArray): Boolean {
        val message = "SESSION.KEY".toByteArray() + sessionKey
        return CryptoUtils.verify(cardPublicKey, message, signAttestSession)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OpenSessionWithAccessTokenResponse
        if (accessLevel != other.accessLevel) return false
        if (!signAttestSession.contentEquals(other.signAttestSession)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = accessLevel.hashCode()
        result = 31 * result + signAttestSession.contentHashCode()
        return result
    }
}

/**
 * Second step of the access token secure channel establishment.
 * Opens an encrypted session using challenge-response with access tokens.
 */
class OpenSessionWithAccessTokenCommand(
    private val challengeB: ByteArray,
    private val hmacAttestB: ByteArray,
    private val sessionKey: ByteArray,
) : Command<OpenSessionWithAccessTokenResponse>() {

    override val cardSessionEncryption: CardSessionEncryption = CardSessionEncryption.NONE

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.None

    override fun performPreCheck(card: Card): TangemSdkError? {
        if (card.firmwareVersion < FirmwareVersion.v8) {
            return TangemSdkError.NotSupportedFirmwareVersion()
        }
        return null
    }

    override fun run(session: CardSession, callback: CompletionCallback<OpenSessionWithAccessTokenResponse>) {
        Log.command(this)
        transceive(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    val card = session.environment.card
                    if (card == null) {
                        callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
                        return@transceive
                    }
                    if (result.data.verify(sessionKey, card.cardPublicKey)) {
                        callback(CompletionResult.Success(result.data))
                    } else {
                        callback(CompletionResult.Failure(TangemSdkError.VerificationFailed()))
                    }
                }
                is CompletionResult.Failure -> {
                    when (result.error) {
                        is TangemSdkError.AccessDenied, is TangemSdkError.InvalidState -> {
                            session.secureChannelSession?.reset()
                        }
                    }
                    callback(CompletionResult.Failure(result.error))
                }
            }
        }
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = createTlvBuilder(legacyMode = environment.legacyMode)
        tlvBuilder.append(TlvTag.CardId, environment.card?.cardId)
        tlvBuilder.append(TlvTag.Challenge, challengeB)
        tlvBuilder.append(TlvTag.Hmac, hmacAttestB)
        return CommandApdu(
            ins = Instruction.OpenSession.code,
            tlvs = tlvBuilder.serialize(),
            p1 = 0,
            p2 = EncryptionMode.CcmWithAccessToken.byteValue,
        )
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): OpenSessionWithAccessTokenResponse {
        val decoder = createTlvDecoder(environment, apdu)
        return OpenSessionWithAccessTokenResponse(
            accessLevel = decoder.decode(TlvTag.AccessLevel),
            signAttestSession = decoder.decode(TlvTag.CardSignature),
        )
    }
}
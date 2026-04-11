package com.tangem.operations.securechannel.establish

import com.tangem.Log
import com.tangem.common.CompletionResult
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionEncryption
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.core.TangemSdkError
import com.tangem.common.tlv.TlvTag
import com.tangem.crypto.CryptoUtils
import com.tangem.operations.Command
import com.tangem.operations.CommandResponse
import com.tangem.operations.PreflightReadMode
import com.tangem.operations.resetcode.AuthorizeMode

data class AuthorizeWithSecurityDelayResponse(
    val pubSessionKeyA: ByteArray,
    val signAttestA: ByteArray,
) : CommandResponse {

    fun verify(cardPublicKey: ByteArray): Boolean {
        val message = "SESSION.CARD".toByteArray() + pubSessionKeyA
        return CryptoUtils.verify(cardPublicKey, message, signAttestA)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AuthorizeWithSecurityDelayResponse
        if (!pubSessionKeyA.contentEquals(other.pubSessionKeyA)) return false
        if (!signAttestA.contentEquals(other.signAttestA)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = pubSessionKeyA.contentHashCode()
        result = 31 * result + signAttestA.contentHashCode()
        return result
    }
}

/**
 * First step of the security delay secure channel establishment.
 * Sends an authorize command with [AuthorizeMode.SecureDelay] interaction mode.
 * Returns the card's session public key and attestation signature.
 */
class AuthorizeWithSecurityDelayCommand : Command<AuthorizeWithSecurityDelayResponse>() {

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.None
    override val cardSessionEncryption: CardSessionEncryption = CardSessionEncryption.NONE

    override fun performPreCheck(card: Card): TangemSdkError? {
        if (card.firmwareVersion < FirmwareVersion.v8) {
            return TangemSdkError.NotSupportedFirmwareVersion()
        }
        return null
    }

    override fun run(session: CardSession, callback: CompletionCallback<AuthorizeWithSecurityDelayResponse>) {
        Log.command(this)
        transceive(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    val card = session.environment.card
                    if (card == null) {
                        callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
                        return@transceive
                    }
                    if (result.data.verify(card.cardPublicKey)) {
                        callback(CompletionResult.Success(result.data))
                    } else {
                        callback(CompletionResult.Failure(TangemSdkError.VerificationFailed()))
                    }
                }
                is CompletionResult.Failure -> {
                    callback(CompletionResult.Failure(result.error))
                }
            }
        }
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = createTlvBuilder(legacyMode = environment.legacyMode)
        tlvBuilder.append(TlvTag.CardId, environment.card?.cardId)
        tlvBuilder.append(TlvTag.InteractionMode, AuthorizeMode.SecureDelay)
        return CommandApdu(instruction = Instruction.Authorize, tlvs = tlvBuilder.serialize())
    }

    override fun deserialize(
        environment: SessionEnvironment,
        apdu: ResponseApdu,
    ): AuthorizeWithSecurityDelayResponse {
        val decoder = createTlvDecoder(environment, apdu)
        return AuthorizeWithSecurityDelayResponse(
            pubSessionKeyA = decoder.decode(TlvTag.SessionKeyA),
            signAttestA = decoder.decode(TlvTag.CardSignature),
        )
    }
}
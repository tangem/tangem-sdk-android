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
import com.tangem.crypto.hmacSha256
import com.tangem.crypto.secureCompare
import com.tangem.crypto.xorWith
import com.tangem.operations.Command
import com.tangem.operations.CommandResponse
import com.tangem.operations.PreflightReadMode
import com.tangem.operations.resetcode.AuthorizeMode

interface AuthorizeWithAccessTokenModel : CommandResponse

data class AuthorizeWithAccessTokenResponse(
    val challengeA: ByteArray,
    val hmacAttestA: ByteArray,
) : AuthorizeWithAccessTokenModel {

    fun verify(identifyToken: ByteArray): Boolean {
        val key = identifyToken.xorWith(challengeA)
        val input = "SESSION.CARD".toByteArray() + challengeA
        val hmacCalculated = key.hmacSha256(input)

        if (!hmacCalculated.secureCompare(hmacAttestA)) {
            Log.error { "Card attest HMAC (hmacAttestA) is invalid!" }
            return false
        }

        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AuthorizeWithAccessTokenResponse
        if (!challengeA.contentEquals(other.challengeA)) return false
        if (!hmacAttestA.contentEquals(other.hmacAttestA)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = challengeA.contentHashCode()
        result = 31 * result + hmacAttestA.contentHashCode()
        return result
    }
}

data class AuthorizeWithAccessTokenResponseDTO(
    val challengeWithXor: ByteArray,
    val hmacAttestA: ByteArray,
) : AuthorizeWithAccessTokenModel {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AuthorizeWithAccessTokenResponseDTO
        if (!challengeWithXor.contentEquals(other.challengeWithXor)) return false
        if (!hmacAttestA.contentEquals(other.hmacAttestA)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = challengeWithXor.contentHashCode()
        result = 31 * result + hmacAttestA.contentHashCode()
        return result
    }
}

/**
 * First step of the access token secure channel establishment.
 * Sends an authorize command with [AuthorizeMode.AccessToken] interaction mode.
 * Returns a challenge and HMAC attestation from the card.
 */
class AuthorizeWithAccessTokensCommand : Command<AuthorizeWithAccessTokenModel>() {

    override val cardSessionEncryption: CardSessionEncryption = CardSessionEncryption.NONE

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.None

    override fun performPreCheck(card: Card): TangemSdkError? {
        if (card.firmwareVersion < FirmwareVersion.v8) {
            return TangemSdkError.NotSupportedFirmwareVersion()
        }
        if (card.settings.isBackupRequired && card.backupStatus?.isActive != true) {
            return TangemSdkError.WalletUnavailableBackupRequired()
        }
        return null
    }

    override fun run(session: CardSession, callback: CompletionCallback<AuthorizeWithAccessTokenModel>) {
        Log.command(this)
        transceive(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    val dto = result.data as? AuthorizeWithAccessTokenResponseDTO ?: run {
                        callback(CompletionResult.Failure(TangemSdkError.InvalidTokensResponseData()))
                        return@transceive
                    }
                    val accessTokens = session.environment.cardAccessTokens
                    if (accessTokens == null) {
                        callback(CompletionResult.Failure(TangemSdkError.MissingAccessTokens()))
                        return@transceive
                    }

                    val challengeA = dto.challengeWithXor.xorWith(accessTokens.identifyToken)
                    val response = AuthorizeWithAccessTokenResponse(
                        challengeA = challengeA,
                        hmacAttestA = dto.hmacAttestA,
                    )

                    if (response.verify(accessTokens.identifyToken)) {
                        callback(CompletionResult.Success(response))
                    } else {
                        session.resetAccessTokens()
                        session.secureChannelSession?.reset()
                        callback(CompletionResult.Failure(TangemSdkError.InvalidAccessTokens()))
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
        tlvBuilder.append(TlvTag.InteractionMode, AuthorizeMode.AccessToken)
        return CommandApdu(instruction = Instruction.Authorize, tlvs = tlvBuilder.serialize())
    }

    override fun deserialize(
        environment: SessionEnvironment,
        apdu: ResponseApdu,
    ): AuthorizeWithAccessTokenResponseDTO {
        val decoder = createTlvDecoder(environment, apdu)
        return AuthorizeWithAccessTokenResponseDTO(
            challengeWithXor = decoder.decode(TlvTag.Challenge),
            hmacAttestA = decoder.decode(TlvTag.Hmac),
        )
    }
}
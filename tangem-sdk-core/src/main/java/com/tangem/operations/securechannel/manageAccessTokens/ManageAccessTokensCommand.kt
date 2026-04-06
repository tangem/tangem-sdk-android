package com.tangem.operations.securechannel.manageAccessTokens

import com.tangem.Log
import com.tangem.common.CompletionResult
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.*
import com.tangem.common.tlv.InteractionMode
import com.tangem.common.tlv.TlvTag
import com.tangem.common.v8.CardAccessTokens
import com.tangem.operations.Command
import com.tangem.operations.CommandResponse

data class ManageAccessTokensResponse(
    val accessToken: ByteArray,
    val identifyToken: ByteArray,
) : CommandResponse {

    fun isZeroResponse(): Boolean {
        return accessToken.all { it == 0x00.toByte() } || identifyToken.all { it == 0x00.toByte() }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ManageAccessTokensResponse

        if (!accessToken.contentEquals(other.accessToken)) return false
        if (!identifyToken.contentEquals(other.identifyToken)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = accessToken.contentHashCode()
        result = 31 * result + identifyToken.contentHashCode()
        return result
    }
}

enum class ManageAccessTokensMode : InteractionMode {
    GET {
        override val rawValue: Byte
            get() = 0x00
    },
    RENEW {
        override val rawValue: Byte
            get() = 0x01
    },
    RESET {
        override val rawValue: Byte
            get() = 0x02
    }
}

class ManageAccessTokensCommand(private val mode: ManageAccessTokensMode) : Command<ManageAccessTokensResponse>() {

    override var cardSessionEncryption = CardSessionEncryption.SECURE_CHANNEL_WITH_PIN

    override fun performPreCheck(card: Card): TangemSdkError? {
        if (card.firmwareVersion < FirmwareVersion.v8) {
            return TangemSdkError.NotSupportedFirmwareVersion()
        }

        if (card.settings.isBackupRequired
            && card.backupStatus?.isActive == false
        ) {
            return TangemSdkError.NoActiveBackup()
        }

        return null
    }

    override fun run(session: CardSession, callback: CompletionCallback<ManageAccessTokensResponse>) {
        transceive(session) { result ->
            when (result) {
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                is CompletionResult.Success -> {
                    saveTokens(result.data, session)
                    callback(CompletionResult.Success(result.data))
                }
            }
        }
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = createTlvBuilder(legacyMode = environment.legacyMode)
        tlvBuilder.append(tag = TlvTag.InteractionMode, value = mode.rawValue)

        return CommandApdu(instruction = Instruction.ManageAccessTokens, tlvs = tlvBuilder.serialize())
    }

    override fun deserialize(
        environment: SessionEnvironment,
        apdu: ResponseApdu,
    ): ManageAccessTokensResponse {
        val decoder = createTlvDecoder(environment, apdu)
        return ManageAccessTokensResponse(
            accessToken = decoder.decode(TlvTag.AccessToken),
            identifyToken = decoder.decode(TlvTag.IdentifyToken),
        )
    }

    private fun saveTokens(response: ManageAccessTokensResponse, session: CardSession) {
        if (response.isZeroResponse() || mode == ManageAccessTokensMode.RESET) {
            session.resetAccessTokens()
            Log.command("Access tokens reset successfully")
        } else {
            session.environment.cardAccessTokens = CardAccessTokens(response)
            session.saveAccessTokensIfNeeded()
            Log.command("Access tokens updated successfully")
        }
    }
}
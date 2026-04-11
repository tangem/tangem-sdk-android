package com.tangem.operations.securechannel.establish

import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.AccessLevel
import com.tangem.common.core.CardSessionEncryption
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.core.TangemError
import com.tangem.common.core.TangemSdkError
import com.tangem.common.encryption.EncryptionMode
import com.tangem.common.tlv.TlvTag
import com.tangem.operations.Command
import com.tangem.operations.CommandResponse
import com.tangem.operations.PreflightReadMode

data class OpenSessionWithSecurityDelayResponse(
    val accessLevel: AccessLevel,
) : CommandResponse

/**
 * Completes the secure channel establishment with security delay.
 * Sends session key B to the card and receives the access level.
 */
class OpenSessionWithSecurityDelayCommand(
    private val sessionKeyB: ByteArray,
) : Command<OpenSessionWithSecurityDelayResponse>() {

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.None
    override val cardSessionEncryption: CardSessionEncryption = CardSessionEncryption.NONE

    override fun performPreCheck(card: Card): TangemError? {
        if (card.firmwareVersion < FirmwareVersion.v8) {
            return TangemSdkError.NotSupportedFirmwareVersion()
        }

        return null
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = createTlvBuilder(legacyMode = environment.legacyMode)
        tlvBuilder.append(TlvTag.CardId, environment.card?.cardId)
        tlvBuilder.append(TlvTag.SessionKeyB, sessionKeyB)
        return CommandApdu(
            ins = Instruction.OpenSession.code,
            tlvs = tlvBuilder.serialize(),
            p1 = 0,
            p2 = EncryptionMode.CcmWithSecurityDelay.byteValue,
        )
    }

    override fun deserialize(
        environment: SessionEnvironment,
        apdu: ResponseApdu,
    ): OpenSessionWithSecurityDelayResponse {
        val decoder = createTlvDecoder(environment, apdu)
        return OpenSessionWithSecurityDelayResponse(
            accessLevel = decoder.decode(TlvTag.AccessLevel),
        )
    }
}
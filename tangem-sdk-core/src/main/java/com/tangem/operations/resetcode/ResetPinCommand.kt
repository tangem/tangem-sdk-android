package com.tangem.operations.resetcode

import com.tangem.common.SuccessResponse
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.calculateSha256
import com.tangem.common.tlv.TlvTag
import com.tangem.operations.Command
import com.tangem.operations.PreflightReadMode

/**
 */
class ResetPinCommand(
    private val accessCode: ByteArray,
    private val passcode: ByteArray,
) : Command<SuccessResponse>() {

    override fun requiresPasscode(): Boolean = false
    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.ReadCardOnly

    override fun performPreCheck(card: Card): TangemSdkError? {
        if (card.firmwareVersion < FirmwareVersion.BackupAvailable) {
            return TangemSdkError.NotSupportedFirmwareVersion()
        }
        if (card.backupStatus !is Card.BackupStatus.Active) {
            return TangemSdkError.NoActiveBackup()
        }
        return null
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val card = environment.card ?: throw TangemSdkError.MissingPreflightRead()

        val tlvBuilder = createTlvBuilder(environment.legacyMode)

        if (card.firmwareVersion < FirmwareVersion.v8) {
            tlvBuilder.append(TlvTag.CardId, environment.card?.cardId)
            tlvBuilder.append(TlvTag.NewPin, accessCode)
            tlvBuilder.append(TlvTag.NewPin2, passcode)
            tlvBuilder.append(TlvTag.Hash, (accessCode + passcode).calculateSha256())
        } else {
            tlvBuilder.append(TlvTag.NewPin, accessCode)
            tlvBuilder.append(TlvTag.Hash, accessCode.calculateSha256())
        }

        return CommandApdu(Instruction.SetPin, tlvBuilder.serialize())
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): SuccessResponse {
        val decoder = createTlvDecoder(environment, apdu)
        return SuccessResponse(
            cardId = decoder.decode(TlvTag.CardId),
        )
    }
}
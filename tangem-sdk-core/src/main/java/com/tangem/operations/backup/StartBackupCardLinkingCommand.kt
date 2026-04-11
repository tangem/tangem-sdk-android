package com.tangem.operations.backup

import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.core.TangemSdkError
import com.tangem.common.tlv.TlvTag
import com.tangem.operations.Command

/**
 */
class StartBackupCardLinkingCommand(
    private val primaryCardLinkingKey: ByteArray,
) : Command<BackupCard>() {

    override val allowsRequestAccessCodeFromRepository: Boolean
        get() = false

    override fun requiresPasscode(): Boolean = false

    override fun performPreCheck(card: Card): TangemSdkError? {
        if (card.firmwareVersion < FirmwareVersion.BackupAvailable) {
            return TangemSdkError.BackupFailedFirmware()
        }
        if (!card.settings.isBackupAllowed) {
            return TangemSdkError.BackupNotAllowed()
        }
        if (card.backupStatus?.canBackup != true) {
            return TangemSdkError.BackupFailedAlreadyCreated()
        }
        if (card.wallets.isNotEmpty()) {
            return TangemSdkError.BackupFailedNotEmptyWallets(cardId = card.cardId)
        }
        return null
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val card = environment.card ?: throw TangemSdkError.MissingPreflightRead()

        val tlvBuilder = createTlvBuilder(environment.legacyMode)
        tlvBuilder.append(TlvTag.PrimaryCardLinkingKey, primaryCardLinkingKey)

        if (shouldAddPin(environment.accessCode, card.firmwareVersion)) {
            tlvBuilder.append(TlvTag.Pin, environment.accessCode.value)
        }
        if (card.firmwareVersion < FirmwareVersion.v8) {
            tlvBuilder.append(TlvTag.CardId, environment.card?.cardId)
        }

        return CommandApdu(Instruction.StartBackupCardLinking, tlvBuilder.serialize())
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): BackupCard {

        val card = environment.card ?: throw TangemSdkError.UnknownError()

        val decoder = createTlvDecoder(environment, apdu)
        return BackupCard(
            cardId = decoder.decode(TlvTag.CardId),
            cardPublicKey = card.cardPublicKey,
            linkingKey = decoder.decode(TlvTag.BackupCardLinkingKey),
            firmwareVersion = card.firmwareVersion,
            attestSignature = decoder.decode(TlvTag.CardSignature),
            certificate = null,
            batchId = card.batchId,
        )
    }
}
package com.tangem.operations.backup

import com.tangem.Log
import com.tangem.common.CompletionResult
import com.tangem.common.UserCode
import com.tangem.common.UserCodeType
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.guard

@Suppress("LongParameterList")
class FinalizePrimaryCardTask(
    private val backupCards: List<BackupCard>,
    private val accessCode: ByteArray,
    private val passcode: ByteArray,
    private val attestSignature: ByteArray?, // We already have attestSignature
    private val onLink: (ByteArray) -> Unit,
    private val onRead: (String, List<EncryptedBackupData>) -> Unit,
    private val onFinalize: () -> Unit,
    private val readBackupStartIndex: Int,
) : CardSessionRunnable<Card> {

    override val allowsRequestAccessCodeFromRepository: Boolean
        get() = false

    private lateinit var card: Card

    override fun run(session: CardSession, callback: CompletionCallback<Card>) {
        val completion: CompletionCallback<Card> = { result ->
            if (result is CompletionResult.Success) {
                session.cacheBackupAccessDataIfNeeded(accessCode) { callback(result) }
            } else {
                callback(result)
            }
        }

        card = session.environment.card.guard {
            completion(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
            return
        }
        val backupStatus = card.backupStatus.guard {
            completion(CompletionResult.Failure(TangemSdkError.NotSupportedFirmwareVersion()))
            return
        }

        val linkAction = getLinkAction(backupStatus)

        if (linkAction == LinkAction.RETRY) { // We should swap codes only if they were set on the card.
            if (card.isAccessCodeSet) {
                session.environment.accessCode = UserCode(UserCodeType.AccessCode, accessCode)
            }
            if (card.isPasscodeSet == true) {
                session.environment.passcode = UserCode(UserCodeType.Passcode, passcode)
            }
        }

        if (linkAction != LinkAction.SKIP) {
            val command = LinkBackupCardsCommand(
                backupCards = backupCards,
                accessCode = accessCode,
                passcode = passcode,
            )
            command.run(session) { linkResult ->
                when (linkResult) {
                    is CompletionResult.Success -> {
                        onLink(linkResult.data.attestSignature)
                        readBackupData(session = session, index = 0, callback = completion)
                    }
                    is CompletionResult.Failure -> completion(CompletionResult.Failure(linkResult.error))
                }
            }
        } else {
            readBackupData(session = session, index = readBackupStartIndex, callback = completion)
        }
    }

    private fun readBackupData(session: CardSession, index: Int, callback: CompletionCallback<Card>) {
        if (index > backupCards.lastIndex) {
            finalizeBackupData(session, callback)
            return
        }
        val currentBackupCard = backupCards[index]
        ReadBackupDataCommand(
            backupCardLinkingKey = currentBackupCard.linkingKey,
            accessCode = accessCode,
        ).run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    onRead(currentBackupCard.cardId, result.data.data)
                    readBackupData(session, index + 1, callback)
                }
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }
        }
    }

    private fun finalizeBackupData(session: CardSession, callback: CompletionCallback<Card>) {
        val card = session.environment.card.guard {
            callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
            return
        }
        if (card.firmwareVersion < FirmwareVersion.KeysImportAvailable) {
            onFinalize()
            callback(CompletionResult.Success(card))
            return
        }
        FinalizeReadBackupDataCommand(accessCode).run(session) { result ->
            when (result) {
                is CompletionResult.Failure -> {
                    // Backup data already finalized,
                    // but we didn't catch the original response due to NFC errors or tag lost.
                    // Just cover invalid state error
                    if (result.error is TangemSdkError.InvalidState) {
                        Log.debug { "Got ${result.error}. Ignoring.." }
                        onFinalize()
                        updateBackupStatusToActive(session)
                        callback(CompletionResult.Success(card))
                    } else {
                        callback(CompletionResult.Failure(result.error))
                    }
                }
                is CompletionResult.Success -> {
                    onFinalize()
                    updateBackupStatusToActive(session)
                    callback(CompletionResult.Success(card))
                }
            }
        }
    }

    /**
     * The backup of the primary card is completed at this point, but the card object in the
     * environment still holds the status read at preflight. Sync it, so that the completion of the
     * task can request card access tokens (the card issues them only when its backup is active).
     */
    private fun updateBackupStatusToActive(session: CardSession) {
        session.environment.card = session.environment.card?.copy(
            backupStatus = Card.BackupStatus.Active(cardsCount = backupCards.size),
        )
    }

    private fun getLinkAction(status: Card.BackupStatus): LinkAction {
        return when (status) {
            is Card.BackupStatus.Active, is Card.BackupStatus.CardLinked -> {
                if (attestSignature != null) {
                    // We already have attest signature and card already linked. Can skip linking
                    LinkAction.SKIP
                } else {
                    // We don't have attest signature, but card already linked.
                    // Force retry with new user codes
                    LinkAction.RETRY
                }
            }
            Card.BackupStatus.NoBackup -> {
                LinkAction.LINK
            }
        }
    }

    enum class LinkAction {
        LINK,
        SKIP,
        RETRY,
    }
}
package com.tangem.operations.backup

import com.tangem.common.CompletionResult
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.guard
import com.tangem.common.extensions.toMapKey
import com.tangem.operations.derivation.DeriveMultipleWalletPublicKeysTask
import com.tangem.operations.read.ReadWalletsListCommand

class FinalizeBackupCardTask(
    private val primaryCard: PrimaryCard,
    private val backupCards: List<BackupCard>,
    private val backupData: List<EncryptedBackupData>,
    private val attestSignature: ByteArray,
    private val accessCode: ByteArray,
    private val passcode: ByteArray,
    private val performDerivations: Boolean,
) : CardSessionRunnable<Card> {

    override val allowsRequestAccessCodeFromRepository: Boolean
        get() = false

    override fun run(session: CardSession, callback: CompletionCallback<Card>) {
        val card = session.environment.card.guard {
            callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
            return
        }

        when (card.backupStatus) {
            is Card.BackupStatus.NoBackup -> {
                val command = LinkPrimaryCardCommand(
                    primaryCard = primaryCard,
                    backupCards = backupCards,
                    attestSignature = attestSignature,
                    accessCode = accessCode,
                    passcode = passcode,
                )

                command.run(session) { linkResult ->
                    when (linkResult) {
                        is CompletionResult.Success -> {
                            writeBackupData(session, callback)
                        }

                        is CompletionResult.Failure -> callback(CompletionResult.Failure(linkResult.error))
                    }
                }
            }

            is Card.BackupStatus.Active -> {
                // Inconsistence case. The card status is ok, but sdk status is incompleted.
                // We should check all wallets later on the app side.
                readWallets(session, callback)
            }

            else -> {
                // only an interrupted case is possible
                writeBackupData(session, callback)
            }
        }
    }

    private fun writeBackupData(session: CardSession, callback: CompletionCallback<Card>) {
        val writeCommand = WriteBackupDataCommand(backupData, accessCode, passcode)

        writeCommand.run(session) { writeResult ->
            when (writeResult) {
                is CompletionResult.Success -> {
                    if (writeResult.data.backupStatus == Card.BackupRawStatus.Active) {
                        readWallets(session, callback)
                    } else {
                        callback(CompletionResult.Failure(TangemSdkError.UnknownError()))
                    }
                }

                is CompletionResult.Failure ->
                    callback(CompletionResult.Failure(writeResult.error))
            }
        }
    }

    private fun readWallets(session: CardSession, callback: CompletionCallback<Card>) {
        ReadWalletsListCommand().run(session) { result ->
            when (result) {
                is CompletionResult.Success -> deriveKeysIfNeeded(session, callback)

                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }
        }
    }

    private fun deriveKeysIfNeeded(session: CardSession, callback: CompletionCallback<Card>) {
        val card = session.environment.card.guard {
            callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
            return
        }

        if (!performDerivations) {
            callback(CompletionResult.Success(card))
            return
        }

        if (card.assertWalletsAccess() != null) {
            callback(CompletionResult.Success(card))
            return
        }

        val defaultPaths = session.environment.config.defaultDerivationPaths

        if (card.firmwareVersion < FirmwareVersion.HDWalletAvailable ||
            !card.settings.isHDWalletAllowed || defaultPaths.isEmpty()
        ) {
            callback(CompletionResult.Success(card))
            return
        }

        val derivations = card.wallets.mapNotNull { wallet ->
            val paths = defaultPaths[wallet.curve]
            val key = wallet.publicKey ?: return@mapNotNull null
            if (!paths.isNullOrEmpty()) key.toMapKey() to paths else null
        }.toMap()

        if (derivations.isEmpty()) {
            callback(CompletionResult.Success(card))
            return
        }

        DeriveMultipleWalletPublicKeysTask(derivations).run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    val derivedCard = session.environment.card.guard {
                        callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
                        return@run
                    }
                    callback(CompletionResult.Success(derivedCard))
                }

                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }
        }
    }
}
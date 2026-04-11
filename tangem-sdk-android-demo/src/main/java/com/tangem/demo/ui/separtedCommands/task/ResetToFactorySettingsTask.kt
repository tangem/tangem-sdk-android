package com.tangem.demo.ui.separtedCommands.task

import com.tangem.common.CompletionResult
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.guard
import com.tangem.operations.backup.ResetBackupCommand
import com.tangem.operations.masterSecret.PurgeMasterSecretCommand
import com.tangem.operations.securechannel.manageAccessTokens.ResetAccessTokensTask
import com.tangem.operations.wallet.PurgeWalletCommand

class ResetToFactorySettingsTask : CardSessionRunnable<Card> {

    override fun run(session: CardSession, callback: (result: CompletionResult<Card>) -> Unit) {
        deleteWallets(session, callback)
    }

    private fun deleteWallets(session: CardSession, callback: (result: CompletionResult<Card>) -> Unit) {
        val wallet = session.environment.card?.wallets?.lastOrNull().guard {
            deleteMasterSecret(session, callback)
            return
        }

        PurgeWalletCommand(wallet.index).run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    deleteWallets(session, callback)
                }
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }
        }
    }

    private fun deleteMasterSecret(session: CardSession, callback: (result: CompletionResult<Card>) -> Unit) {
        if (session.environment.card?.masterSecret == null) {
            resetBackup(session, callback)
            return
        }

        PurgeMasterSecretCommand().run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    resetBackup(session, callback)
                }
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }
        }
    }

    private fun resetBackup(session: CardSession, callback: (result: CompletionResult<Card>) -> Unit) {
        val card = session.environment.card.guard {
            callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
            return
        }

        val backupStatus = card.backupStatus
        if (backupStatus == null || backupStatus == Card.BackupStatus.NoBackup) {
            resetAccessTokens(session, callback)
            return
        }

        ResetBackupCommand().run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    val card = session.environment.card.guard {
                        callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
                        return@run
                    }
                    callback(CompletionResult.Success(card))
                }
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }
        }
    }

    private fun resetAccessTokens(session: CardSession, callback: (result: CompletionResult<Card>) -> Unit) {
        val card = session.environment.card.guard {
            callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
            return
        }

        if (card.firmwareVersion < FirmwareVersion.v8) {
            callback(CompletionResult.Success(card))
            return
        }

        ResetAccessTokensTask().run(session) {
            when (it) {
                is CompletionResult.Success -> {
                    if (session.environment.card == null) {
                        callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
                    } else {
                        callback(CompletionResult.Success(card))
                    }
                }
                is CompletionResult.Failure -> callback(CompletionResult.Failure(it.error))
            }
        }
    }
}
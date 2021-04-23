package com.tangem.tasks

import com.tangem.CardSession
import com.tangem.CardSessionRunnable
import com.tangem.FirmwareConstraints
import com.tangem.Log
import com.tangem.commands.common.card.Card
import com.tangem.commands.read.ReadCommand
import com.tangem.commands.read.ReadWalletCommand
import com.tangem.commands.read.ReadWalletListCommand
import com.tangem.commands.wallet.CardWallet
import com.tangem.commands.wallet.WalletIndex
import com.tangem.common.CompletionResult

/**
[REDACTED_AUTHOR]
 */
interface PreflightReadCapable {
    fun needPreflightRead(): Boolean = true
    fun preflightReadSettings(): PreflightReadSettings = PreflightReadSettings.ReadCardOnly
}

sealed class PreflightReadSettings {

    object ReadCardOnly : PreflightReadSettings() {
        override fun toString(): String = this::class.java.simpleName
    }

    data class ReadWallet(val walletIndex: WalletIndex) : PreflightReadSettings() {
        override fun toString(): String = walletIndex.toString()
    }

    object FullCardRead : PreflightReadSettings() {
        override fun toString(): String = this::class.java.simpleName
    }
}

class PreflightReadTask(
    private val readSettings: PreflightReadSettings
) : CardSessionRunnable<Card> {

    override val requiresPin2: Boolean = false

    override fun run(session: CardSession, callback: (result: CompletionResult<Card>) -> Unit) {
        Log.debug { "================ Perform preflight check with settings: $readSettings) ================" }
        ReadCommand().run(session) { result ->
            when (result) {
                is CompletionResult.Success -> finalizeRead(session, result.data, callback)
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }
        }
    }

    private fun finalizeRead(session: CardSession, card: Card, callback: (result: CompletionResult<Card>) -> Unit) {
        if (card.firmwareVersion < FirmwareConstraints.AvailabilityVersions.walletData ||
            readSettings == PreflightReadSettings.ReadCardOnly) {
            callback(CompletionResult.Success(card))
            return
        }

        fun handleSuccess(card: Card, wallets: List<CardWallet>, callback: (result: CompletionResult<Card>) -> Unit) {
            session.environment.card = card
            card.setWallets(wallets)
            callback(CompletionResult.Success(card))
        }

        when (readSettings) {
            is PreflightReadSettings.ReadWallet -> {
                ReadWalletCommand(readSettings.walletIndex).run(session) {
                    when (it) {
                        is CompletionResult.Success -> handleSuccess(card, listOf(it.data.wallet), callback)
                        is CompletionResult.Failure -> callback(CompletionResult.Failure(it.error))
                    }
                }
            }
            PreflightReadSettings.FullCardRead -> {
                ReadWalletListCommand().run(session) {
                    when (it) {
                        is CompletionResult.Success -> handleSuccess(card, it.data.wallets, callback)
                        is CompletionResult.Failure -> callback(CompletionResult.Failure(it.error))
                    }
                }
            }
        }
    }
}
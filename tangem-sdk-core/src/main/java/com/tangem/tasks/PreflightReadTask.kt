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
 * Mode for preflight read task
 * Note: Valid for cards with COS v.4 and higher. Older card will always read the card and the wallet info.
 * `FullCardRead` will be used by default
 */
sealed class PreflightReadMode {

    /**
     * No card will be read at session start. `SessionEnvironment.card` will be empty
     */
    object None : PreflightReadMode()

    /**
     * Read only card info without wallet info. Valid for cards with COS v.4 and higher.
     * Older card will always read card and wallet info
     */
    object ReadCardOnly : PreflightReadMode()

    /**
     * Read card info and single wallet specified in associated index `WalletIndex`.
     * Valid for cards with COS v.4 and higher. Older card will always read card and wallet info
     */
    data class ReadWallet(val walletIndex: WalletIndex) : PreflightReadMode() {
        override fun toString(): String = walletIndex.toString()
    }

    /**
     * Read card info and all wallets. Used by default
     */
    object FullCardRead : PreflightReadMode()

    override fun toString(): String = this::class.java.simpleName
}

class PreflightReadTask(
    private val readMode: PreflightReadMode
) : CardSessionRunnable<Card> {

    override fun run(session: CardSession, callback: (result: CompletionResult<Card>) -> Unit) {
        Log.debug { "================ Perform preflight check with settings: $readMode) ================" }
        ReadCommand().run(session) { result ->
            when (result) {
                is CompletionResult.Success -> finalizeRead(session, result.data, callback)
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }
        }
    }

    private fun finalizeRead(session: CardSession, card: Card, callback: (result: CompletionResult<Card>) -> Unit) {
        if (card.firmwareVersion < FirmwareConstraints.AvailabilityVersions.walletData ||
            readMode == PreflightReadMode.ReadCardOnly) {
            callback(CompletionResult.Success(card))
            return
        }

        fun handleSuccess(card: Card, wallets: List<CardWallet>, callback: (result: CompletionResult<Card>) -> Unit) {
            session.environment.card = card
            card.wallets = wallets.toMutableList()
            callback(CompletionResult.Success(card))
        }

        when (readMode) {
            is PreflightReadMode.ReadWallet -> {
                ReadWalletCommand(readMode.walletIndex).run(session) {
                    when (it) {
                        is CompletionResult.Success -> handleSuccess(card, listOf(it.data.wallet), callback)
                        is CompletionResult.Failure -> callback(CompletionResult.Failure(it.error))
                    }
                }
            }
            PreflightReadMode.FullCardRead -> {
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
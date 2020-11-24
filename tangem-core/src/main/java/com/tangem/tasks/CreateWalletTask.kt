package com.tangem.tasks

import com.tangem.*
import com.tangem.commands.*
import com.tangem.commands.common.card.Card
import com.tangem.commands.common.card.CardStatus
import com.tangem.commands.common.card.EllipticCurve
import com.tangem.commands.common.card.FirmwareVersion
import com.tangem.common.CompletionResult
import com.tangem.common.extensions.getFirmwareVersion
import com.tangem.common.extensions.guard

/**
 * Task that allows to create wallet at Tangem card and verify its private key.
 * It performs `CreateWallet` and `CheckWalletCommand`,  subsequently.
 *

 * If index not provided task
 * attempt to create wallet at any empty index, until success or reach max index
 *
 * Note: `WalletConfig` and `WalletIndexPointer` available for cards with COS v.4.0 and higher.
 * @property walletConfig: if not set task will create wallet with settings that was specified in card data
 * while personalization
 * @property walletIndexPointer: If not provided task will attempt to create wallet on default index.
 * If failed - task will keep trying to create.
 */
class CreateWalletTask(
    private val walletConfig: WalletConfig?,
    private var walletIndexPointer: WalletIndexPointer?
) : CardSessionRunnable<CreateWalletResponse>, WalletPointable {

    override val requiresPin2 = false

    override var walletPointer: WalletPointer? = walletIndexPointer

    private var firstAttemptWalletIndex: Int? = null
    private var shouldCreateAtAnyIndex: Boolean = false

    override fun run(session: CardSession, callback: (result: CompletionResult<CreateWalletResponse>) -> Unit) {
        val card = session.environment.card.guard {
            callback(CompletionResult.Failure(TangemSdkError.CardError()))
            return
        }
        var curve = card.curve.guard {
            callback(CompletionResult.Failure(TangemSdkError.CardError()))
            return
        }

        val firmwareVersion = session.environment.card?.getFirmwareVersion() ?: FirmwareVersion.zero
        if (firmwareVersion >= FirmwareConstraints.AvailabilityVersions.walletData) {
            walletConfig?.curveId?.let { curve = it }
            shouldCreateAtAnyIndex = walletIndexPointer == null
        }

        createWallet(session, card, walletIndexPointer ?: WalletIndexPointer(0), curve, callback)
    }

    private fun createWallet(
        session: CardSession,
        card: Card,
        index: WalletIndexPointer,
        curve: EllipticCurve,
        callback: (result: CompletionResult<CreateWalletResponse>) -> Unit
    ) {
        val command = CreateWalletCommand(walletConfig, walletIndexPointer)
        command.run(session) { createWalletResult ->
            when (createWalletResult) {
                is CompletionResult.Success -> {
                    if (createWalletResult.data.status != CardStatus.Loaded) {
                        callback(CompletionResult.Failure(TangemSdkError.UnknownError()))
                    } else {
                        val checkWalletCommand = CheckWalletCommand(
                            curve, createWalletResult.data.walletPublicKey, walletPointer
                        )
                        checkWalletCommand.run(session) { result ->
                            when (result) {
                                is CompletionResult.Success -> callback(createWalletResult)
                                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                            }
                        }
                    }
                }
                is CompletionResult.Failure -> {
                    val error = createWalletResult.error
                    if (shouldCreateAtAnyIndex) {
                        when (error) {
                            is TangemSdkError.AlreadyCreated, is TangemSdkError.CardIsPurged, is TangemSdkError.InvalidState -> {
                                val nextIndex = updateWalletPointerToNext(index, card.walletsCount).guard {
                                    callback(CompletionResult.Failure(TangemSdkError.MaxNumberOfWalletsCreated()))
                                    return@run
                                }
                                walletIndexPointer = nextIndex
                                createWallet(session, card, nextIndex, curve, callback)
                                return@run
                            }
                            else -> {
                                Log.e(this::class.java.simpleName, "Default error case while creating wallet $error")
                            }
                        }
                    }
                    callback(CompletionResult.Failure(error))
                }
            }
        }
    }

    private fun updateWalletPointerToNext(currentPointer: WalletIndexPointer?, walletsCount: Int?): WalletIndexPointer? {
        val currentIndex = currentPointer?.index ?: return null
        val walletsCount = walletsCount ?: return null

        var isFirstAttempt = false

        if (firstAttemptWalletIndex == null) {
            firstAttemptWalletIndex = currentIndex
            isFirstAttempt = true
        }
        var newIndex: Int

        if (isFirstAttempt && currentIndex != 0) {
            newIndex = 0
        } else {
            newIndex = currentIndex + 1
            newIndex += if (firstAttemptWalletIndex == newIndex) 1 else 0
        }

        return if (newIndex >= walletsCount) null else WalletIndexPointer(newIndex)
    }
}
package com.tangem.tasks

import com.tangem.CardSession
import com.tangem.CardSessionRunnable
import com.tangem.TagType
import com.tangem.TangemSdkError
import com.tangem.commands.*
import com.tangem.commands.common.CardDeserializer
import com.tangem.common.CompletionResult
import com.tangem.common.extensions.getFirmwareNumber

/**
 * Task that allows to read Tangem card and verify its private key.
 *
 * It performs two commands, [ReadCommand] and [CheckWalletCommand], subsequently.
 */
class ScanTask : CardSessionRunnable<Card> {

    override val requiresPin2 = false

    override fun run(session: CardSession, callback: (result: CompletionResult<Card>) -> Unit) {
        if (session.connectedTag == TagType.Slix) {
            readSlixTag(session, callback)
            return
        }
        val card = session.environment.card
        if (card == null) {
            callback(CompletionResult.Failure(TangemSdkError.CardError()))
            return
        }
        card.isPin1Default = (session.environment.pin1?.isDefault == true)

//        if (session.environment.pin1 != null && session.environment.pin2 != null) {
//            val checkPinCommand = SetPinCommand(session.environment.pin1!!.value, session.environment.pin2!!.value)
//            checkPinCommand.run(session) { result ->
//                when (result) {
//                    is CompletionResult.Failure -> {
//                        if (result.error is TangemSdkError.Pin2OrCvcRequired) {
//                            session.environment.pin2 = null
//                        }
//                    }
//                }
//                runCheckWalletIfNeeded(card, session, callback)
//            }
//        } else {

        val firmwareNumber = card.getFirmwareNumber()
        if (firmwareNumber != null && firmwareNumber > 1.19) { // >1.19 cards without SD on CheckPin
            CheckPinCommand().run(session) { result ->
                when (result) {
                    is CompletionResult.Success -> {
                        card.isPin2Default = result.data.isPin2Default
                        session.environment.card = card
                        runCheckWalletIfNeeded(card, session, callback)
                    }
                    is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                }
            }
        } else {
            session.environment.card = card
            runCheckWalletIfNeeded(card, session, callback)
        }
    }

    private fun runCheckWalletIfNeeded(
            card: Card, session: CardSession,
            callback: (result: CompletionResult<Card>) -> Unit
    ) {
        if (card.cardData?.productMask?.contains(Product.Tag) != false) {
            callback(CompletionResult.Success(card))

        } else if (card.status != CardStatus.Loaded) {
            callback(CompletionResult.Success(card))

        } else if (card.curve == null || card.walletPublicKey == null) {
            callback(CompletionResult.Failure(TangemSdkError.CardError()))

        } else {
            val checkWalletCommand = CheckWalletCommand(card.curve, card.walletPublicKey)
            checkWalletCommand.run(session) { result ->
                when (result) {
                    is CompletionResult.Success -> callback(CompletionResult.Success(card))
                    is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                }
            }
        }
    }

    private fun readSlixTag(session: CardSession, callback: (result: CompletionResult<Card>) -> Unit) {
        session.readSlixTag { result ->
            when (result) {
                is CompletionResult.Success -> {
                    try {
                        val card = CardDeserializer.deserialize(result.data)
                        callback(CompletionResult.Success(card))
                    } catch (error: TangemSdkError) {
                        callback(CompletionResult.Failure(error))
                    }
                }
                is CompletionResult.Failure -> {
                    callback(CompletionResult.Failure(result.error))
                }
            }
        }
    }
}
package com.tangem.commands

import com.tangem.*
import com.tangem.commands.common.card.Card
import com.tangem.common.CompletionResult
import com.tangem.common.PinCode
import com.tangem.common.apdu.*
import com.tangem.common.extensions.toInt
import com.tangem.common.tlv.TlvTag
import java.util.*


interface ApduSerializable<T : CommandResponse> {
    /**
     * Serializes data into an array of [com.tangem.common.tlv.Tlv],
     * then creates [CommandApdu] with this data.
     * @param environment [SessionEnvironment] of the current card
     * @return command data converted to [CommandApdu] that allows to convert it to [ByteArray]
     * that can be sent to a Tangem card
     */
    fun serialize(environment: SessionEnvironment): CommandApdu

    /**
     * Deserializes data received from a card and stored in [ResponseApdu]
     * into an array of [com.tangem.common.tlv.Tlv]. Then maps it into a [CommandResponse].
     * @param environment [SessionEnvironment] of the current card.
     * @param apdu received data.
     * @return Card response converted to a [CommandResponse] of a type [T]
     */
    fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): T
}

/**
 * Basic interface for a parsed response from [Command].
 */
interface CommandResponse

data class SimpleResponse(val cardId: String) : CommandResponse

/**
 * Basic class for Tangem card commands
 */
abstract class Command<T : CommandResponse> : ApduSerializable<T>, CardSessionRunnable<T> {

    open val performPreflightRead: Boolean = true
    override val requiresPin2: Boolean = false

    override fun run(session: CardSession, callback: (result: CompletionResult<T>) -> Unit) {
        val commandLog = "Send command: ${this::class.java.simpleName}"
        Log.command { "=".repeat(commandLog.length) }
        Log.command { commandLog }
        Log.command { "=".repeat(commandLog.length) }
        transceive(session, callback)
    }

    open fun performPreCheck(card: Card): TangemError? = null

    open fun mapError(card: Card?, error: TangemError): TangemError = error

    fun transceive(session: CardSession, callback: (result: CompletionResult<T>) -> Unit) {
        val card = session.environment.card
        if (card == null && performPreflightRead) {
            callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
            return
        }
        if (session.environment.handleErrors && card != null) {
            performPreCheck(card)?.let { error ->
                callback(CompletionResult.Failure(error))
                return
            }
        }
        if (session.environment.pin2 == null && requiresPin2) {
            requestPin(PinType.Pin2, session, callback)
            return
        }
        transceiveInternal(session, callback)
    }

    private fun transceiveInternal(session: CardSession, callback: (result: CompletionResult<T>) -> Unit) {
        Log.apdu { "------ Serialize command -------" }
        val apdu = serialize(session.environment)
        Log.apdu { "--------------------------------" }
        showMissingSecurityDelay(session)
        transceiveApdu(apdu, session) { result ->
            when (result) {
                is CompletionResult.Failure -> {
                    if (result.error is TangemSdkError.ExtendedLengthNotSupported) {
                        if (session.environment.terminalKeys != null) {
                            session.environment.terminalKeys = null
                            transceiveInternal(session, callback)
                            return@transceiveApdu
                        }
                    }
                    if (session.environment.handleErrors) {
                        val error = mapError(session.environment.card, result.error)
                        if (error is TangemSdkError.Pin1Required) {
                            requestPin(PinType.Pin1, session, callback)
                            return@transceiveApdu
                        }
                        if (error is TangemSdkError.Pin2OrCvcRequired) {
                            requestPin(PinType.Pin2, session, callback)
                            return@transceiveApdu
                        }
                        callback(CompletionResult.Failure(error))
                        return@transceiveApdu

                    }
                    callback(CompletionResult.Failure(result.error))
                }
                is CompletionResult.Success -> {
                    try {
                        Log.apdu { "------- Deserialize response -------" }
                        val response = deserialize(session.environment, result.data)
                        Log.apdu { "------------------------------------" }
                        callback(CompletionResult.Success(response))
                    } catch (error: TangemSdkError) {
                        callback(CompletionResult.Failure(error))
                    }
                }
            }
        }
    }

    private fun transceiveApdu(
        apdu: CommandApdu,
        session: CardSession,
        callback: (result: CompletionResult<ResponseApdu>) -> Unit
    ) {
        session.send(apdu) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    val responseApdu = result.data

                    when (responseApdu.statusWord) {
                        StatusWord.ProcessCompleted,
                        StatusWord.Pin1Changed, StatusWord.Pin2Changed, StatusWord.Pins12Changed,
                        StatusWord.Pin3Changed, StatusWord.Pins13Changed, StatusWord.Pins23Changed,
                        StatusWord.Pins123Changed -> {
                            callback(CompletionResult.Success(responseApdu))
                        }
                        StatusWord.NeedPause -> {
                            // NeedPause is returned from the card whenever security delay is triggered.
                            val remainingTime = deserializeSecurityDelay(responseApdu)
                            if (remainingTime != null) {
                                session.viewDelegate.onSecurityDelay(
                                    remainingTime,
                                    session.environment.card?.pauseBeforePin2 ?: 0
                                )
                            }
                            transceiveApdu(apdu, session, callback)
                        }
                        StatusWord.NeedEncryption -> {
                            when (session.environment.encryptionMode) {
                                EncryptionMode.NONE -> {
                                    Log.session { "Try change to fast encryption" }
                                    session.environment.encryptionKey = null
                                    session.environment.encryptionMode = EncryptionMode.FAST
                                }
                                EncryptionMode.FAST -> {
                                    Log.session { "Try change to strong encryption" }
                                    session.environment.encryptionKey = null
                                    session.environment.encryptionMode = EncryptionMode.STRONG
                                }
                                EncryptionMode.STRONG -> {
                                    callback(CompletionResult.Failure(TangemSdkError.NeedEncryption()))
                                    return@send
                                }
                            }
                            transceiveApdu(apdu, session, callback)
                        }
                        else -> {
                            val error = responseApdu.statusWord.toTangemSdkError()
                            if (error != null) {
                                callback(CompletionResult.Failure(error))
                            } else {
                                callback(CompletionResult.Failure(TangemSdkError.UnknownError()))
                            }
                        }
                    }
                }
                is CompletionResult.Failure ->
                    if (result.error is TangemSdkError.TagLost) {
                        session.viewDelegate.onTagLost()
                    } else {
                        callback(CompletionResult.Failure(result.error))
                    }
            }
        }
    }

    @Deprecated("Used to fix lack of security delay on cards with firmware version below 1.21")
    private fun showMissingSecurityDelay(session: CardSession) {
        if (!session.environment.enableMissingSecurityDelay) return

        var totalDuration = session.environment.card?.pauseBeforePin2 ?: 0
        totalDuration = if (totalDuration == 0) 0 else totalDuration + (totalDuration / 3)
        session.viewDelegate.onSecurityDelay(SessionEnvironment.missingSecurityDelayCode, totalDuration)
    }

    /**
     * Helper method to parse security delay information received from a card.
     *
     * @return Remaining security delay in milliseconds.
     */
    private fun deserializeSecurityDelay(
        responseApdu: ResponseApdu
    ): Int? {
        val tlv = responseApdu.getTlvData()
        return tlv?.find { it.tag == TlvTag.Pause }?.value?.toInt()
    }

    private fun requestPin(pinType: PinType,
                           session: CardSession, callback: (result: CompletionResult<T>) -> Unit) {
        session.pause()
        val isFirstAttempt = pinType.isWrongPinEntered(session.environment)
        when (pinType) {
            PinType.Pin1 -> session.environment.pin1 = null
            PinType.Pin2 -> session.environment.pin2 = null
        }
        Log.session { "Request pin of type: $pinType" }
        session.viewDelegate.onPinRequested(pinType, isFirstAttempt) { pin ->
            when (pinType) {
                PinType.Pin1 -> session.environment.pin1 = PinCode(pin)
                PinType.Pin2 -> session.environment.pin2 = PinCode(pin)
            }
            session.resume()
            transceiveInternal(session, callback)
        }
    }
}
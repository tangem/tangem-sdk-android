package com.tangem.operations

import com.tangem.Log
import com.tangem.common.CompletionResult
import com.tangem.common.CompletionResult.Failure
import com.tangem.common.UserCode
import com.tangem.common.UserCodeType
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.apdu.StatusWord
import com.tangem.common.apdu.toTangemSdkError
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.*
import com.tangem.common.core.TangemSdkError.DeserializeApduFailed
import com.tangem.common.core.TangemSdkError.NeedEncryption
import com.tangem.common.encryption.EncryptionMode
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.extensions.toHexString
import com.tangem.common.extensions.toInt
import com.tangem.common.tlv.Tlv
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag

/**
 * Basic interface for a parsed response from [Command].
 */
interface CommandResponse

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

    fun createTlvDecoder(environment: SessionEnvironment, apdu: ResponseApdu): TlvDecoder {
        val tlvData = apdu.getTlvData()?.toMutableList() ?: throw DeserializeApduFailed()

        // V8 protocol requires no cardId in response for all commands.
        // Add it manually for compatibility.
        val card = environment.card
        if (card != null && card.firmwareVersion >= FirmwareVersion.v8) {
            tlvData.add(Tlv(TlvTag.CardId, card.cardId.hexToBytes()))
        }

        return TlvDecoder(tlvData)
    }

    /**
     * Fix nfc issues with long-running commands and security delay for iPhone 7/7+. Card firmware 2.39
     *     4 - Timeout setting for ping nfc-module
     */
    fun createTlvBuilder(legacyMode: Boolean): TlvBuilder {
        val builder = TlvBuilder()
        if (legacyMode) {
            builder.append(tag = TlvTag.LegacyMode, value = 4)
        }
        return builder
    }

    fun shouldAddPin(value: UserCode, firmwareVersion: FirmwareVersion): Boolean {
        if (firmwareVersion >= FirmwareVersion.v8) {
            return false
        }

        if (firmwareVersion >= FirmwareVersion.isDefaultPinsOptional && value.isDefault()) {
            return false
        }

        return true
    }
}

/**
 * Basic class for Tangem card commands
 */
abstract class Command<T : CommandResponse> : ApduSerializable<T>, CardSessionRunnable<T> {

    open fun requiresPasscode(): Boolean = false

    override fun run(session: CardSession, callback: CompletionCallback<T>) {
        Log.command(this)
        transceive(session, callback)
    }

    protected open fun performPreCheck(card: Card): TangemError? = null

    open fun mapError(card: Card?, error: TangemError): TangemError = error

    fun transceive(session: CardSession, callback: CompletionCallback<T>) {
        val card = session.environment.card
        if (preflightReadMode() != PreflightReadMode.None && card == null) {
            callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
            return
        }
        if (session.environment.config.handleErrors && card != null) {
            performPreCheck(card)?.let { error ->
                callback(CompletionResult.Failure(error))
                return
            }
        }

        if (session.environment.passcode.value == null && requiresPasscode()) {
            requestPin(UserCodeType.Passcode, session, callback)
        } else {
            transceiveInternal(session, callback)
        }
    }

    @Suppress("ComplexMethod")
    private fun transceiveInternal(session: CardSession, callback: CompletionCallback<T>) {
        session.rememberTag()

        Log.apduCommand { "C-APDU serialization..." }
        val apdu = serialize(session.environment)
        Log.apduCommand { "C-APDU serialization complete" }
        showMissingSecurityDelay(session)
        transceiveApdu(apdu, session) { result ->
            when (result) {
                is CompletionResult.Failure -> {
                    val environment = session.environment
                    if (result.error is TangemSdkError.ExtendedLengthNotSupported && environment.terminalKeys != null) {
                        environment.terminalKeys = null
                        transceiveInternal(session, callback)
                        return@transceiveApdu
                    }

                    val firmwareVersion = environment.card?.firmwareVersion
                    if (firmwareVersion != null && firmwareVersion >= FirmwareVersion.v8) {
                        when (result.error) {
                            is TangemSdkError.RetrySecureChannelNeeded -> {
                                session.secureChannelSession?.reset()
                                if (apdu.ins == Instruction.Authorize.code ||
                                    apdu.ins == Instruction.OpenSession.code
                                ) {
                                    Log.debug { "Fail secure channel command to restart the full flow" }
                                    session.releaseTag()
                                    callback(CompletionResult.Failure(result.error))
                                } else {
                                    Log.debug { "Retry command with new secure channel session" }
                                    transceiveInternal(session, callback)
                                }
                            }
                            else -> {
                                session.releaseTag()
                                callback(CompletionResult.Failure(result.error))
                            }
                        }
                        return@transceiveApdu
                    }

                    val error = when {
                        environment.config.handleErrors -> mapError(environment.card, result.error)
                        else -> result.error
                    }
                    when (error) {
                        is TangemSdkError.AccessCodeRequired -> requestPin(UserCodeType.AccessCode, session, callback)
                        is TangemSdkError.PasscodeRequired -> requestPin(UserCodeType.Passcode, session, callback)
                        is TangemSdkError.InvalidParams -> {
                            if (requiresPasscode()) {
                                // Addition check for COS v4 and newer to prevent false-positive pin2 request
                                val isPasscodeSet = environment.card?.isPasscodeSet == false
                                if (isPasscodeSet && !environment.isUserCodeSet(UserCodeType.Passcode)) {
                                    session.releaseTag()
                                    callback(CompletionResult.Failure(error))
                                } else {
                                    requestPin(UserCodeType.Passcode, session, callback)
                                }
                            } else {
                                session.releaseTag()
                                callback(CompletionResult.Failure(error))
                            }
                        }
                        else -> {
                            session.releaseTag()
                            callback(CompletionResult.Failure(error))
                        }
                    }
                }
                is CompletionResult.Success -> {
                    session.releaseTag()
                    session.secureChannelSession?.incrementPacketCounter()
                    // Should be incremented only for success responses. Do not increment on security delay responses.
                    session.secureChannelSession?.packetCounter?.let {
                        Log.session { "Current packet counter is $it" }
                    }
                    try {
                        Log.apduCommand { "R-APDU deserialization..." }
                        val response = deserialize(session.environment, result.data)
                        Log.apduCommand { "R-APDU deserialization complete" }
                        callback(CompletionResult.Success(response))
                    } catch (error: TangemSdkError) {
                        callback(CompletionResult.Failure(error))
                    }
                }
            }
        }
    }

    @Suppress("LongMethod", "ComplexMethod")
    private fun transceiveApdu(apdu: CommandApdu, session: CardSession, callback: CompletionCallback<ResponseApdu>) {
        session.establishEncryptionIfNeeded(
            cardSessionEncryption = cardSessionEncryption,
            shouldAskForAccessCode = shouldAskForAccessCode,
        ) { encryptionResult ->
            when (encryptionResult) {
                is CompletionResult.Success -> {
                    Log.session { "Encryption established successfully" }
                    session.send(apdu) { result ->
                        when (result) {
                            is CompletionResult.Success -> {
                                val responseApdu = result.data

                                when (responseApdu.statusWord) {
                                    StatusWord.ProcessCompleted,
                                    StatusWord.Pin1Changed, StatusWord.Pin2Changed, StatusWord.Pins12Changed,
                                    StatusWord.Pin3Changed, StatusWord.Pins13Changed, StatusWord.Pins23Changed,
                                    StatusWord.Pins123Changed,
                                    -> {
                                        if (session.environment.currentSecurityDelay != null) {
                                            session.environment.currentSecurityDelay = null
                                        }
                                        callback(CompletionResult.Success(responseApdu))
                                    }
                                    StatusWord.NeedPause -> {
                                        // NeedPause is returned from the card whenever security delay is triggered.
                                        val securityDelayResponse = deserializeSecurityDelay(responseApdu)
                                        if (securityDelayResponse != null) {
                                            if (session.environment.currentSecurityDelay == null) {
                                                val fw = session.environment.card?.firmwareVersion
                                                val isInstantSecurityDelay =
                                                    fw?.let { it >= FirmwareVersion.BackupAvailable } ?: false
                                                session.environment.currentSecurityDelay = if (isInstantSecurityDelay) {
                                                    securityDelayResponse.remainingSeconds
                                                } else {
                                                    securityDelayResponse.remainingSeconds + 1f
                                                }
                                            }
                                            val totalSd = session.environment.currentSecurityDelay!!
                                            if (totalSd > 0) {
                                                session.viewDelegate.onSecurityDelay(
                                                    ms = (securityDelayResponse.remainingSeconds * CENTISECONDS_TO_MS)
                                                        .toInt(),
                                                    totalDurationSeconds = totalSd.toInt(),
                                                    productType = session.environment.config.productType,
                                                )
                                            }
                                            if (securityDelayResponse.saveToFlash &&
                                                session.environment.encryptionMode == EncryptionMode.None
                                            ) {
                                                session.pause()
                                                session.resume()
                                            }
                                        }
                                        transceiveApdu(apdu, session, callback)
                                    }
                                    StatusWord.NeedEncryption -> {
                                        when (session.environment.encryptionMode) {
                                            EncryptionMode.None -> {
                                                Log.session { "Try change to fast encryption" }
                                                session.environment.encryptionKey = null
                                                session.environment.encryptionMode = EncryptionMode.Fast
                                            }
                                            EncryptionMode.Fast -> {
                                                Log.session { "Try change to strong encryption" }
                                                session.environment.encryptionKey = null
                                                session.environment.encryptionMode = EncryptionMode.Strong
                                            }
                                            EncryptionMode.Strong -> {
                                                callback(Failure(NeedEncryption()))
                                                return@send
                                            }
                                            EncryptionMode.CcmWithSecurityDelay,
                                            EncryptionMode.CcmWithAccessToken,
                                            EncryptionMode.CcmWithAsymmetricKeys,
                                            -> {
                                                // Should not happen
                                                callback(Failure(NeedEncryption()))
                                                return@send
                                            }
                                        }
                                        transceiveApdu(apdu, session, callback)
                                    }
                                    StatusWord.Unknown -> {
                                        callback(
                                            CompletionResult.Failure(
                                                TangemSdkError.UnknownStatus(responseApdu.sw.toHexString()),
                                            ),
                                        )
                                    }
                                    StatusWord.AccessDenied -> {
                                        callback(CompletionResult.Failure(TangemSdkError.AccessDenied()))
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
                                    session.viewDelegate.onTagLost(session.environment.config.productType)
                                } else {
                                    callback(CompletionResult.Failure(result.error))
                                }
                        }
                    }
                }
                is CompletionResult.Failure -> {
                    callback(CompletionResult.Failure(encryptionResult.error))
                }
            }
        }
    }

    @Deprecated("Used to fix lack of security delay on cards with firmware version below 1.21")
    private fun showMissingSecurityDelay(session: CardSession) {
        if (!session.environment.enableMissingSecurityDelay) return

        var totalDuration = session.environment.card!!.settings.securityDelay
        totalDuration = if (totalDuration == 0) 0 else totalDuration + totalDuration.div(other = 3)
        session.viewDelegate.onSecurityDelay(
            ms = SessionEnvironment.missingSecurityDelayCode,
            totalDurationSeconds = totalDuration,
            productType = session.environment.config.productType,
        )
    }

    /**
     * Helper method to parse security delay information received from a card.
     *
     * @return Security delay response with remaining seconds and save-to-flash flag.
     */
    private fun deserializeSecurityDelay(responseApdu: ResponseApdu): SecurityDelayResponse? {
        val tlv = responseApdu.getTlvData() ?: return null
        val remainingCs = tlv.find { it.tag == TlvTag.Pause }?.value?.toInt() ?: return null
        val seconds = remainingCs / CENTISECONDS_IN_SECOND
        val saveToFlash = tlv.any { it.tag == TlvTag.Flash }
        return SecurityDelayResponse(seconds, saveToFlash)
    }

    private fun requestPin(type: UserCodeType, session: CardSession, callback: CompletionCallback<T>) {
        session.handleWrongUserCode(type) { result ->
            when (result) {
                is CompletionResult.Success -> transceiveInternal(session, callback)
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }
        }
    }

    protected fun deserializeApdu(environment: SessionEnvironment, apdu: ResponseApdu): List<Tlv> {
        return createTlvDecoder(environment, apdu).tlvList
    }

    private data class SecurityDelayResponse(
        val remainingSeconds: Float,
        val saveToFlash: Boolean,
    )

    private companion object {
        const val CENTISECONDS_IN_SECOND = 100f
        const val CENTISECONDS_TO_MS = 10
    }
}
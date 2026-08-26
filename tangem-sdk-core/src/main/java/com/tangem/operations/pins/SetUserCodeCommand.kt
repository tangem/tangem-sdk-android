package com.tangem.operations.pins

import com.tangem.Log
import com.tangem.common.*
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.apdu.StatusWord
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionEncryption
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.calculateSha256
import com.tangem.common.tlv.TlvTag
import com.tangem.operations.Command
import com.tangem.operations.securechannel.manageAccessTokens.ManageAccessTokensTask

class SetUserCodeCommand private constructor() : Command<SuccessResponse>() {

    override val cardSessionEncryption: CardSessionEncryption = CardSessionEncryption.SECURE_CHANNEL_WITH_PIN

    internal var shouldRestrictDefaultCodes = true

    internal var isPasscodeRequire = true
    private var codes = mutableMapOf<UserCodeType, UserCodeAction>()

    override fun requiresPasscode(): Boolean = isPasscodeRequire

    override fun prepare(session: CardSession, callback: CompletionCallback<Unit>) {
        requestIfNeeded(UserCodeType.AccessCode, session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    requestIfNeeded(UserCodeType.Passcode, session) { result ->
                        when (result) {
                            is CompletionResult.Success -> callback(CompletionResult.Success(Unit))
                            is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                        }
                    }
                }
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }
        }
    }

    override fun run(session: CardSession, callback: CompletionCallback<SuccessResponse>) {
        if (codes.values.contains(UserCodeAction.Request)) { // If prepare not called e.g. chaining
            val error = getPauseError(session.environment)
            if (error == null) {
                session.pause()
            } else {
                session.pause()
            }

            prepare(session) { result ->
                when (result) {
                    is CompletionResult.Success -> session.resume { runCommand(session, callback) }
                    is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                }
            }
        } else {
            runCommand(session, callback)
        }
    }

    private fun runCommand(session: CardSession, callback: CompletionCallback<SuccessResponse>) {
        // Restrict default codes except reset command
        if (shouldRestrictDefaultCodes) {
            if (!isCodeAllowed(UserCodeType.AccessCode)) {
                callback(CompletionResult.Failure(TangemSdkError.AccessCodeCannotBeChanged()))
                return
            }
            if (!isCodeAllowed(UserCodeType.Passcode)) {
                callback(CompletionResult.Failure(TangemSdkError.PasscodeCannotBeChanged()))
                return
            }
        }

        super.run(session) { result ->
            result
                .doOnSuccess { response ->
                    val accessCodeValue = codes[UserCodeType.AccessCode]?.value
                    if (accessCodeValue != null) {
                        session.environment.accessCode = UserCode(UserCodeType.AccessCode, accessCodeValue)
                    }

                    val passcodeValue = codes[UserCodeType.Passcode]?.value
                    if (passcodeValue != null) {
                        session.environment.passcode = UserCode(UserCodeType.Passcode, passcodeValue)
                    }

                    refreshAccessTokensIfNeeded(session) {
                        callback(CompletionResult.Success(response))
                    }
                }
                .doOnFailure { error -> callback(CompletionResult.Failure(error)) }
        }
    }

    /**
     * The card may invalidate previously issued access tokens when its access code changes, which
     * would leave the tokens saved on the phone stale and force an access code prompt on the next
     * scan. Verify the tokens with the card in the same session and renew them if they were reset.
     * A failure is logged and does not fail the command — the user code is already changed.
     */
    private fun refreshAccessTokensIfNeeded(session: CardSession, callback: () -> Unit) {
        val card = session.environment.card
        val isAccessCodeChanged = codes[UserCodeType.AccessCode]?.value != null

        if (card == null || card.firmwareVersion < FirmwareVersion.v8 || !isAccessCodeChanged) {
            callback()
            return
        }

        // The card issues tokens only when its backup is active
        if (card.settings.isBackupRequired && card.backupStatus?.isActive != true) {
            callback()
            return
        }

        Log.session { "Refresh access tokens after access code change" }
        ManageAccessTokensTask().run(session) { result ->
            result.doOnFailure { error ->
                Log.error { "Failed to refresh access tokens after access code change: $error" }
            }
            callback()
        }
    }

    private fun isCodeAllowed(type: UserCodeType): Boolean {
        return !codes[type]?.value.contentEquals(type.defaultValue.calculateSha256())
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val card = environment.card ?: throw TangemSdkError.MissingPreflightRead()

        val accessCodeValue = codes[UserCodeType.AccessCode]?.value ?: environment.accessCode.value
        // val passcodeValue = codes[UserCodeType.Passcode]?.value ?: environment.passcode.value
        if (accessCodeValue == null) {
            throw TangemSdkError.SerializeCommandError()
        }

        val tlvBuilder = createTlvBuilder(environment.legacyMode)

        when {
            card.firmwareVersion >= FirmwareVersion.v8 -> {
                tlvBuilder.append(TlvTag.NewPin, accessCodeValue)
                tlvBuilder.append(TlvTag.Hash, accessCodeValue.calculateSha256())
            }
            card.firmwareVersion >= FirmwareVersion.BackupAvailable -> {
                val passcodeValue = codes[UserCodeType.Passcode]?.value ?: environment.passcode.value
                    ?: throw TangemSdkError.SerializeCommandError()

                tlvBuilder.append(TlvTag.CardId, environment.card?.cardId)
                tlvBuilder.append(TlvTag.NewPin, accessCodeValue)
                tlvBuilder.append(TlvTag.NewPin2, passcodeValue)
                tlvBuilder.append(TlvTag.Hash, (accessCodeValue + passcodeValue).calculateSha256())
            }
            else -> {
                val passcodeValue = codes[UserCodeType.Passcode]?.value ?: environment.passcode.value
                    ?: throw TangemSdkError.SerializeCommandError()

                tlvBuilder.append(TlvTag.CardId, environment.card?.cardId)
                tlvBuilder.append(TlvTag.NewPin, accessCodeValue)
                tlvBuilder.append(TlvTag.NewPin2, passcodeValue)
            }
        }

        if (shouldAddPin(environment.accessCode, card.firmwareVersion)) {
            tlvBuilder.append(TlvTag.Pin, environment.accessCode.value)
        }

        if (shouldAddPin(environment.passcode, card.firmwareVersion)) {
            tlvBuilder.append(TlvTag.Pin2, environment.passcode.value)
        }

        return CommandApdu(Instruction.SetPin, tlvBuilder.serialize())
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): SuccessResponse {
        val decoder = createTlvDecoder(environment, apdu)
        return SuccessResponse(decoder.decode(TlvTag.CardId))
    }

    private fun getPauseError(environment: SessionEnvironment): TangemSdkError? {
        val filtered = codes.filter { it.value == UserCodeAction.Request }
        val type = filtered.keys.firstOrNull() ?: return null

        return TangemSdkError.from(type, environment)
    }

    private fun requestIfNeeded(type: UserCodeType, session: CardSession, callback: CompletionCallback<Unit>) {
        if (codes[type] != UserCodeAction.Request) {
            callback(CompletionResult.Success(Unit))
            return
        }

        val formattedCid = session.cardId?.let {
            CardIdFormatter(session.environment.config.cardIdDisplayFormat).getFormattedCardId(it)
        }

        session.viewDelegate.requestUserCodeChange(type, formattedCid) { result ->
            when (result) {
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                is CompletionResult.Success -> {
                    codes[type] = UserCodeAction.StringValue(result.data)
                    callback(CompletionResult.Success(Unit))
                }
            }
        }
    }

    companion object {
        fun changeAccessCode(accessCode: String?): SetUserCodeCommand {
            return SetUserCodeCommand().apply {
                codes[UserCodeType.AccessCode] = accessCode?.let { UserCodeAction.StringValue(it) }
                    ?: UserCodeAction.Request
                codes[UserCodeType.Passcode] = UserCodeAction.NotChange
            }
        }

        fun changePasscode(passcode: String?): SetUserCodeCommand {
            return SetUserCodeCommand().apply {
                codes[UserCodeType.AccessCode] = UserCodeAction.NotChange
                codes[UserCodeType.Passcode] = passcode?.let { UserCodeAction.StringValue(it) }
                    ?: UserCodeAction.Request
            }
        }

        fun change(accessCode: String?, passcode: String?): SetUserCodeCommand {
            return change(accessCode?.calculateSha256(), passcode?.calculateSha256())
        }

        fun change(accessCode: ByteArray?, passcode: ByteArray?): SetUserCodeCommand {
            return SetUserCodeCommand().apply {
                codes[UserCodeType.AccessCode] = accessCode?.let { UserCodeAction.Value(it) }
                    ?: UserCodeAction.Request
                codes[UserCodeType.Passcode] = passcode?.let { UserCodeAction.Value(it) }
                    ?: UserCodeAction.Request
            }
        }

        fun resetAccessCodeCommand(): SetUserCodeCommand {
            return changeAccessCode(UserCodeType.AccessCode.defaultValue).apply {
                shouldRestrictDefaultCodes = false
            }
        }

        fun resetPasscodeCommand(): SetUserCodeCommand {
            return changePasscode(UserCodeType.Passcode.defaultValue).apply {
                shouldRestrictDefaultCodes = false
            }
        }

        fun resetUserCodes(): SetUserCodeCommand {
            return change(UserCodeType.AccessCode.defaultValue, UserCodeType.Passcode.defaultValue).apply {
                shouldRestrictDefaultCodes = false
            }
        }
    }

    sealed class UserCodeAction(val value: ByteArray?) {
        object Request : UserCodeAction(null)
        class StringValue(value: String) : UserCodeAction(value.calculateSha256())
        class Value(data: ByteArray) : UserCodeAction(data)
        object NotChange : UserCodeAction(null)
    }
}

enum class SetPinStatus {
    PinsNotChanged,
    Pin1Changed,
    Pin2Changed,
    Pin3Changed,
    Pins12Changed,
    Pins13Changed,
    Pins23Changed,
    Pins123Changed,
    ;

    companion object {
        fun fromStatusWord(statusWord: StatusWord): SetPinStatus? {
            return when (statusWord) {
                StatusWord.ProcessCompleted -> PinsNotChanged
                StatusWord.Pin1Changed -> Pin1Changed
                StatusWord.Pin2Changed -> Pin2Changed
                StatusWord.Pins12Changed -> Pins12Changed
                StatusWord.Pin3Changed -> Pin3Changed
                StatusWord.Pins13Changed -> Pins13Changed
                StatusWord.Pins23Changed -> Pins23Changed
                StatusWord.Pins123Changed -> Pins123Changed
                else -> null
            }
        }
    }
}
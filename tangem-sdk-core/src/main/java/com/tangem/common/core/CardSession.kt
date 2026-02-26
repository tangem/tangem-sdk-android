package com.tangem.common.core

import com.tangem.*
import com.tangem.common.*
import com.tangem.common.apdu.Apdu
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.EncryptionMode
import com.tangem.common.extensions.VoidCallback
import com.tangem.common.extensions.calculateSha256
import com.tangem.common.json.JSONRPCConverter
import com.tangem.common.json.JSONRPCLinker
import com.tangem.common.nfc.CardReader
import com.tangem.common.services.secure.SecureStorage
import com.tangem.common.usersCode.UserCodeRepository
import com.tangem.crypto.EncryptionHelper
import com.tangem.crypto.pbkdf2Hash
import com.tangem.operations.OpenSessionCommand
import com.tangem.operations.PreflightReadMode
import com.tangem.operations.PreflightReadTask
import com.tangem.operations.preflightread.CardIdPreflightReadFilter
import com.tangem.operations.preflightread.PreflightReadFilter
import com.tangem.operations.read.ReadCommand
import com.tangem.operations.resetcode.ResetCodesController
import com.tangem.operations.resetcode.ResetPinService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.coroutines.resume

/**
 * Allows interaction with Tangem cards. Should be opened before sending commands.
 *
 * @property environment
 * @property reader  is an interface that is responsible for NFC connection and
 * transfer of data to and from the Tangem Card.
 * @property viewDelegate is an  interface that allows interaction with users and shows relevant UI.
 * @property cardId ID, Unique Tangem card ID number. If not null, the SDK will check that you the card
 * with which you tapped a phone has this [cardId] and SDK will return
 * the [TangemSdkError.WrongCardNumber] otherwise.
 * @property initialMessage A custom description that will be shown at the beginning of the NFC session.
 * If null, a default header and text body will be used.
 */
@Suppress("LargeClass", "LongParameterList")
class CardSession(
    cardId: String? = null,
    val viewDelegate: SessionViewDelegate,
    val environment: SessionEnvironment,
    val userCodeRepository: UserCodeRepository?,
    val preflightReadFilter: PreflightReadFilter?,
    private val reader: CardReader,
    private val jsonRpcConverter: JSONRPCConverter,
    private val secureStorage: SecureStorage,
    private var initialMessage: ViewDelegateMessage? = null,
) {

    private val sendCommandApduBridge: Channel<Pair<CommandApdu, CompletionCallback<ResponseApdu>>> = Channel()

    var cardId: String? = cardId
        private set
    var connectedTag: TagType? = null
        private set
    var state = CardSessionState.Inactive
        private set

    private var resetCodesController: ResetCodesController? = null

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO) + CoroutineExceptionHandler { _, throwable ->
        handleScopeCoroutineException(throwable)
    }

    private var preflightReadMode: PreflightReadMode = PreflightReadMode.FullCardRead

    private var onTagConnectedAfterResume: VoidCallback? = null

    fun setInitialMessage(message: ViewDelegateMessage?) {
        initialMessage = message
    }

    fun setMessage(message: ViewDelegateMessage?) {
        viewDelegate.setMessage(message)
    }

    /**
     * This method starts a card session, performs preflight [ReadCommand],
     * invokes [CardSessionRunnable.run] and closes the session.
     * @param iconScanRes iconResource to replace on Scan Bottom Sheet
     * @param runnable [CardSessionRunnable] that will be performed in the session.
     * @param callback will be triggered with a [CompletionResult] of a session.
     */
    fun <T : CardSessionRunnable<R>, R> startWithRunnable(
        iconScanRes: Int? = null,
        runnable: T,
        callback: CompletionCallback<R>,
    ): Unit = runBlocking {
        val result = startWithRunnable(iconScanRes, runnable)
        callback(result)
    }

    suspend fun <T : CardSessionRunnable<R>, R> startWithRunnable(
        iconScanRes: Int? = null,
        runnable: T,
    ): CompletionResult<R> {
        if (state != CardSessionState.Inactive) {
            val error = TangemSdkError.Busy()
            Log.error { "$error" }
            return CompletionResult.Failure(error)
        }

        channelFlow<SessionStartedInner> {
            suspend fun finish(result: SessionStartedInner) {
                channel.send(result)
                channel.close()
            }

            suspend fun finishWithError(error: TangemError, sessionErrorStage: SessionErrorMoment) {
                val result = SessionStartedInner(
                    session = this@CardSession,
                    error = error,
                    sessionErrorStage = sessionErrorStage
                )
                finish(result)
            }

            Log.session { "start card session with runnable" }
            val prepareResult = prepareSession(runnable)
            if (prepareResult is CompletionResult.Failure) {
                finishWithError(prepareResult.error, SessionErrorMoment.Preparation)
                return@channelFlow
            }

            val sessionStartedResult = start2(iconScanRes)
            Log.session { "start runnable" }

            val tagFlow: SharedFlow<TagType?> = reader.tag
                .receiveAsFlow()
                .shareIn(this, SharingStarted.Eagerly)
            val latestTagFlow: SharedFlow<TagType?> = tagFlow.shareIn(
                scope = this,
                started = SharingStarted.Eagerly,
                replay = 1
            )

            suspend fun latestTag() = latestTagFlow.first()
            // wait initial nfc tag not null
            tagFlow.filterNotNull().first()

            tagFlow.runningReduce { previous, new ->
                if (new == null && previous != null && state == CardSessionState.Active) {
                    environment.encryptionKey = null
                }
                new
            }.launchIn(this)

            val selectAppletResult = selectApplet()
            if (selectAppletResult is CompletionResult.Failure) {
                finishWithError(selectAppletResult.error, SessionErrorMoment.AppletSelection)
                return@channelFlow
            }

            if (latestTag() == TagType.Nfc && preflightReadMode != PreflightReadMode.None) {
                preflightCheck { latestTag() }
            }

            val runnableResult = runnable.run(this@CardSession)
            Log.session { "runnable completed" }
            if (runnableResult is CompletionResult.Failure) {
                finishWithError(runnableResult.error, SessionErrorMoment.End)
                return@channelFlow
            }

        }

            .first()

        Log.session { "start card session with runnable" }
        val prepareResult = prepareSession(runnable)
        if (prepareResult is CompletionResult.Failure) {
            stopWithError(prepareResult.error, SessionErrorMoment.Preparation)
            return CompletionResult.Failure(prepareResult.error)
        }
        val sessionStarted = start2(iconScanRes)
        Log.session { "start runnable" }
        when (val prepareResult = prepareSession(runnable)) {
                is CompletionResult.Success -> {
                    val sessionStarted = start(iconScanRes)
                    val error = sessionStarted.error
                        if (error != null) {
                            Log.error { "$error" }
                            return CompletionResult.Failure(error)
                        }
                    val result = runnable.run(this)
                            Log.session { "runnable completed" }
                            when (result) {
                                is CompletionResult.Success -> {
                                    stop()
                                }
                                is CompletionResult.Failure -> {
                                    stopWithError(result.error, SessionErrorMoment.End)
                                }
                            }
                    return result
                }
                is CompletionResult.Failure -> {
                    stopWithError(prepareResult.error, SessionErrorMoment.Preparation)
                    return CompletionResult.Failure(prepareResult.error)
            }
        }
    }

    /**
     * Starts a card session and performs preflight [ReadCommand].
     * @param iconScanRes iconResource to replace on Scan Bottom Sheet
     * @param onSessionStarted: callback with the card session. Can contain [TangemSdkError] if something goes wrong.
     */
    internal suspend fun start2(iconScanRes: Int? = null) {
        Log.session { "start card session with delegate" }
        reader.scope = scope

        state = CardSessionState.Active
        viewDelegate.onSessionStarted(
            cardId = cardId,
            message = initialMessage,
            enableHowTo = environment.config.howToIsEnabled,
            iconScanRes = iconScanRes,
            productType = environment.config.productType,
        )
        reader.startSession()
    }

    /**
     * Starts a card session and performs preflight [ReadCommand].
     * @param iconScanRes iconResource to replace on Scan Bottom Sheet
     * @param onSessionStarted: callback with the card session. Can contain [TangemSdkError] if something goes wrong.
     */
    internal suspend fun start(iconScanRes: Int? = null): SessionStarted {
        Log.session { "start card session with delegate" }
        reader.scope = scope
        reader.startSession()

        state = CardSessionState.Active
        viewDelegate.onSessionStarted(
            cardId = cardId,
            message = initialMessage,
            enableHowTo = environment.config.howToIsEnabled,
            iconScanRes = iconScanRes,
            productType = environment.config.productType,
        )

        scope.launch {
            reader.tag.asFlow()
                .filterNotNull()
                .take(1)
                .collect { tagType ->
                    selectApplet()
                        .doOnSuccess {
                            if (tagType == TagType.Nfc && preflightReadMode != PreflightReadMode.None) {
                                preflightCheck()
                            } else {
                                onSessionStarted(this@CardSession, null)
                            }
                        }
                        .doOnFailure {
                            onSessionStarted(this@CardSession, it)
                            stopWithError(it, SessionErrorMoment.AppletSelection)
                        }
                }
        }

        scope.launch {
            reader.tag.asFlow()
                .drop(1)
                .collect {
                    if (it == null) {
                        viewDelegate.onTagLost(productType = environment.config.productType)
                    } else {
                        viewDelegate.onTagConnected()
                        onTagConnectedAfterResume?.invoke()
                        onTagConnectedAfterResume = null
                    }
                }
        }

        scope.launch {
            reader.tag.asFlow()
                .onCompletion {
                    val exception = it as? CancellationException ?: return@onCompletion
                    val cause = exception.cause ?: return@onCompletion
                    if (cause is TangemSdkError.UserCancelled) {
                        stopWithError(cause, SessionErrorMoment.CancellingByUser)
                        viewDelegate.dismiss()
                        onSessionStarted(this@CardSession, cause)
                    }
                }
                .collect {
                    if (it == null && connectedTag != null && state == CardSessionState.Active) {
                        environment.encryptionKey = null
                        connectedTag = null
                    } else if (it != null) {
                        connectedTag = it
                    }
                }
        }
    }

    fun run(jsonRequest: String, callback: (String) -> Unit) {
        val jsonrpcLinker = JSONRPCLinker(jsonRequest).apply { initRunnable(jsonRpcConverter) }
        if (jsonrpcLinker.hasError()) {
            callback(jsonrpcLinker.response.toJson())
            return
        }

        jsonrpcLinker.runnable!!.run(this) {
            jsonrpcLinker.linkResult(it)
            callback(jsonrpcLinker.response.toJson())
        }
    }

    private suspend fun shouldRequestBiometrics(): Boolean {
        return when {
            userCodeRepository == null -> false
            cardId != null && userCodeRepository.hasSavedUserCode(cardId!!) -> true
            else -> userCodeRepository.hasSavedUserCodes()
        }
    }

    private suspend fun <T : CardSessionRunnable<*>> prepareSession(runnable: T): CompletionResult<Unit> {
        Log.session { "prepare card session" }
        preflightReadMode = runnable.preflightReadMode()
        environment.encryptionMode = runnable.encryptionMode

        Log.session { "User code policy is ${environment.config.userCodeRequestPolicy}" }
        Log.session { "Encryption mode is ${environment.encryptionMode}" }

        if (!runnable.allowsRequestAccessCodeFromRepository) {
            return runnable.prepare(this)
        }

        val successResult = CompletionResult.Success(Unit)
        val userCodeRequestResult = when (val policy = environment.config.userCodeRequestPolicy) {
            is UserCodeRequestPolicy.AlwaysWithBiometrics -> {
                if (shouldRequestBiometrics()) {
                    when (val unlockResult = userCodeRepository?.unlock()) {
                        is CompletionResult.Success -> successResult
                        is CompletionResult.Failure -> {
                            Log.error {
                                """
                                        User codes storage could not be unlocked
                                        |- Cause: ${unlockResult.error}
                                    """.trimIndent()
                            }
                            requestCode(policy.codeType)
                        }
                        else -> requestCode(policy.codeType)
                    }
                } else {
                    requestCode(policy.codeType)
                }
            }
            is UserCodeRequestPolicy.Always -> requestCode(policy.codeType)
            is UserCodeRequestPolicy.Default -> successResult
        }

        return when (userCodeRequestResult) {
            is CompletionResult.Success -> catching { runnable.prepare(this) }
            is CompletionResult.Failure -> userCodeRequestResult
        }
    }

    private suspend fun requestCode(codeType: UserCodeType): CompletionResult<Unit> {
            when (codeType) {
                UserCodeType.AccessCode -> {
                    environment.accessCode = UserCode(codeType, null)
                }
                UserCodeType.Passcode -> {
                    environment.passcode = UserCode(codeType, null)
                }
            }
        return suspendCancellableCoroutine { continuation ->
            requestUserCodeIfNeeded(
                type = codeType,
                isFirstAttempt = true,
            ) { userCodeResult ->
                val result =
                    userCodeResult
                        .doOnSuccess { CompletionResult.Success(Unit) }
                        .doOnFailure { error -> CompletionResult.Failure<Unit>(error) }
                if (continuation.isActive) continuation.resume(result)
            }
        }
    }

    private suspend fun preflightCheck(actualTag: suspend () -> TagType?): SessionStartedInner {
        Log.session { "start preflight check" }

        val preflightTask = PreflightReadTask(
            readMode = preflightReadMode,
            filter = preflightReadFilter ?: cardId?.let(::CardIdPreflightReadFilter),
        )

        when (val result = preflightTask.run(this)) {
                is CompletionResult.Success -> {
                    return SessionStartedInner(this)
                }

                is CompletionResult.Failure -> {
                    val wrongType = when (result.error) {
                        is TangemSdkError.WrongCardType -> WrongValueType.CardType
                        is TangemSdkError.WrongCardNumber -> WrongValueType.CardId(result.error.cardId)
                        else -> null
                    }
                    if (wrongType != null) {
                        Log.error { "${result.error}" }
                        viewDelegate.onWrongCard(wrongType) // todo check it, compare with old, onboarding case?
                            delay(timeMillis = 3500)
                        if (actualTag() == null) {
                            return SessionStartedInner(
                                this@CardSession,
                                result.error,
                                SessionErrorMoment.PreflightCheck
                            )
                            } else {
                                viewDelegate.onTagConnected()
                            return preflightCheck(actualTag)
                        }
                    } else {
                        return SessionStartedInner(this, result.error, SessionErrorMoment.PreflightCheck)
                }
            }
        }
    }

    /**
     * Stops the current session with the text message.
     * @param message If null, the default message will be shown.
     */
    private fun stop(message: Message? = null) {
        viewDelegate.onSessionStopped(message) {
            stopSessionIfActive()
        }
    }

    /**
     * Stops the current session on error.
     * @param error An error that will be shown.
     */
    private fun stopWithError(error: TangemError, sessionErrorStage: SessionErrorMoment) {
        stopSessionIfActive()
        if (error !is TangemSdkError.UserCancelled) {
            Log.error { "Finishing with error occurring at stage ${sessionErrorStage.name}: ${error.code}" }
            viewDelegate.onError(error)
        } else {
            Log.session { "User cancelled NFC session" }
        }
    }

    private fun stopSessionIfActive() {
        if (state == CardSessionState.Inactive) return

        Log.session { "stop session" }
        onStopSessionFinalize()
        reader.stopSession()
        if (scope.isActive) {
            scope.cancel()
        }
    }

    private fun onStopSessionFinalize() {
        state = CardSessionState.Inactive
        preflightReadMode = PreflightReadMode.FullCardRead
        saveUserCodeIfNeeded()
    }


    fun send(apdu: CommandApdu, callback: CompletionCallback<ResponseApdu>) {
        sendCommandApduBridge.trySend(apdu to callback)
    }

    suspend fun send(apdu: CommandApdu): CompletionResult<ResponseApdu> = try {
        establishEncryptionIfNeeded()
        val encryptedApdu = apdu.encrypt(environment.encryptionMode, environment.encryptionKey)
        val responseApdu = reader.transceiveApdu(encryptedApdu)
        when (val result = decrypt(responseApdu)) {
            is CompletionResult.Success -> result
            is CompletionResult.Failure -> {
                Log.error { "${result.error}" } // todo catch is TangemSdkError.TagLost -> Log.session { "tag lost. Waiting for tag..." }
                result
            }
        }
    } catch (throwable: Throwable) {
        val error = throwable as? TangemSdkError ?: TangemSdkError.UnknownError()
        Log.error { "$error" }
        CompletionResult.Failure(error)
    }

    fun pause() {
        reader.pauseSession()
    }

    fun resume(onTagConnected: VoidCallback? = null) {
        onTagConnectedAfterResume = onTagConnected
        reader.resumeSession()
    }

    private suspend fun selectApplet(): CompletionResult<ByteArray?> {
        Log.session { "select the Wallet applet" }
        return reader.transceiveRaw(Apdu.build(Apdu.SELECT, Apdu.TANGEM_WALLET_AID))
    }

    private suspend fun establishEncryptionIfNeeded(): CompletionResult<Boolean> {
        Log.session { "establish encryption if needed" }
        if (environment.encryptionMode == EncryptionMode.None || environment.encryptionKey != null) {
            return CompletionResult.Success(true)
        }

        val encryptionHelper = EncryptionHelper.create(environment.encryptionMode)
            ?: return CompletionResult.Failure(
                TangemSdkError.CryptoUtilsError("Failed to establish encryption"),
            )

        val openSessionCommand = OpenSessionCommand(encryptionHelper.keyA)
        val apdu = openSessionCommand.serialize(environment)

        when (val response = reader.transceiveApdu(apdu)) {
            is CompletionResult.Success -> {
                val result = try {
                    openSessionCommand.deserialize(environment, response.data)
                } catch (error: TangemSdkError) {
                    return CompletionResult.Failure(error)
                }

                val uid = result.uid
                val protocolKey = environment.accessCode.value?.pbkdf2Hash(uid, iterations = 50)
                    ?: return CompletionResult.Failure(
                        TangemSdkError.CryptoUtilsError(
                            "Failed to establish encryption",
                        ),
                    )

                val secret = encryptionHelper.generateSecret(result.sessionKeyB)
                val sessionKey = (secret + protocolKey).calculateSha256()
                environment.encryptionKey = sessionKey

                Log.session { "The encryption established" }
                return CompletionResult.Success(true)
            }
            is CompletionResult.Failure -> {
                Log.session { "establish encryption Failure ${response.error}" }
                return CompletionResult.Failure(response.error)
            }
        }
    }

    private fun decrypt(result: CompletionResult<ResponseApdu>): CompletionResult<ResponseApdu> {
        return when (result) {
            is CompletionResult.Success -> {
                try {
                    CompletionResult.Success(result.data.decrypt(environment.encryptionKey))
                } catch (error: TangemSdkError) {
                    CompletionResult.Failure(error)
                }
            }
            is CompletionResult.Failure -> result
        }
    }

    fun requestUserCodeIfNeeded(type: UserCodeType, isFirstAttempt: Boolean, callback: CompletionCallback<Unit>) {
        val userCode = when (type) {
            UserCodeType.AccessCode -> environment.accessCode.value
            UserCodeType.Passcode -> environment.passcode.value
        }
        if (userCode != null) {
            callback(CompletionResult.Success(Unit))
            return
        }
        Log.session { "request user code of type: $type" }

        val cardId = environment.card?.cardId ?: this.cardId
        val showForgotButton = environment.card?.backupStatus?.isActive ?: false
        val formattedCardId = cardId?.let {
            CardIdFormatter(environment.config.cardIdDisplayFormat).getFormattedCardId(it)
        }

        viewDelegate.requestUserCode(
            type = type,
            isFirstAttempt = isFirstAttempt,
            showForgotButton = showForgotButton,
            cardId = formattedCardId,
        ) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    Log.session { "requestUserCode: Success" }
                    updateEnvironment(type, result.data)
                    callback(CompletionResult.Success(Unit))
                }
                is CompletionResult.Failure -> {
                    Log.session { "requestUserCode: Failure ${result.error}" }
                    if (result.error is TangemSdkError.UserForgotTheCode) {
                        viewDelegate.dismiss()
                        restoreUserCode(type, cardId) { restoreCodeResult ->
                            when (restoreCodeResult) {
                                is CompletionResult.Success -> {
                                    updateEnvironment(type, restoreCodeResult.data)
                                    resetCodesController = null
                                    callback(CompletionResult.Success(Unit))
                                }
                                is CompletionResult.Failure -> {
                                    callback(CompletionResult.Failure(restoreCodeResult.error))
                                }
                            }
                        }
                    } else {
                        callback(CompletionResult.Failure(result.error))
                    }
                }
            }
        }
    }

    fun updateUserCodeIfNeeded() {
        val card = environment.card ?: return
        val userCode = userCodeRepository?.get(card.cardId)
        when {
            userCode?.type == UserCodeType.AccessCode && card.isAccessCodeSet -> {
                environment.accessCode = userCode
            }
            userCode?.type == UserCodeType.Passcode && card.isPasscodeSet == true -> {
                environment.passcode = userCode
            }
        }
    }

    private fun handleScopeCoroutineException(throwable: Throwable) {
        when (throwable) {
            is TangemSdkError.UserCancelled -> {
                onStopSessionFinalize()
            }

            else -> {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val exceptionAsString: String = sw.toString()
                Log.error { exceptionAsString }
                throw throwable
            }
        }
    }

    private fun saveUserCodeIfNeeded() {
        val saveCodeAndLock: suspend (String, UserCode) -> Unit = { cardId, code ->
            userCodeRepository?.save(cardId, code)
                ?.doOnResult {
                    userCodeRepository.lock()
                }
                ?.doOnFailure {
                    Log.error { "Access code saving failed: $it" }
                }
        }

        @Suppress("GlobalCoroutineUsage")
        GlobalScope.launch {
            val card = environment.card
            when {
                card == null -> userCodeRepository?.lock()
                environment.accessCode.value != null && card.isAccessCodeSet -> {
                    saveCodeAndLock(card.cardId, environment.accessCode)
                }
                environment.passcode.value != null && card.isPasscodeSet == true -> {
                    saveCodeAndLock(card.cardId, environment.passcode)
                }
            }
        }
    }

    private fun updateEnvironment(type: UserCodeType, code: String) {
        val userCode = UserCode(type, code)
        environment.encryptionKey = null // we need to reset encryption key with new userCode
        when (type) {
            UserCodeType.AccessCode -> environment.accessCode = userCode
            UserCodeType.Passcode -> environment.passcode = userCode
        }
    }

    private fun restoreUserCode(type: UserCodeType, cardId: String?, callback: CompletionCallback<String>) {
        val sessionBuilder = SessionBuilder(
            viewDelegate = viewDelegate,
            secureStorage = secureStorage,
            userCodeRepository = userCodeRepository,
            reader = reader,
            jsonRpcConverter = jsonRpcConverter,
            preflightReadFilter = null,
        )
        val config = environment.config.apply {
            userCodeRequestPolicy = UserCodeRequestPolicy.Default
        }
        val resetService = ResetPinService(
            sessionBuilder = sessionBuilder,
            stringsLocator = viewDelegate.resetCodesViewDelegate.stringsLocator,
            config = config,
        )
        viewDelegate.resetCodesViewDelegate.stopSessionCallback = {
            stopSessionIfActive()
            callback(CompletionResult.Failure(TangemSdkError.UserCancelled()))
        }
        resetCodesController = ResetCodesController(
            resetService = resetService,
            viewDelegate = viewDelegate.resetCodesViewDelegate,
        ).apply {
            cardIdDisplayFormat = environment.config.cardIdDisplayFormat
            start(codeType = type, cardId = cardId, callback = callback)
        }
    }

    enum class CardSessionState {
        Inactive,
        Active,
    }
}

private typealias SessionStartedCallback = suspend (session: CardSession, error: TangemError?) -> Unit

internal data class SessionStarted(
    val session: CardSession,
    val error: TangemError?,
) {
    // means successfully, useful for view where is using
    constructor(session: CardSession) : this(session, null)
}

internal data class SessionStartedInner(
    val session: CardSession,
    val error: TangemError?,
    val sessionErrorStage: SessionErrorMoment?,
) {
    // means successfully, useful for view where is using
    constructor(session: CardSession) : this(session, null, null)
}

enum class TagType {
    Nfc,
    Slix,
}

class SessionBuilder(
    private val viewDelegate: SessionViewDelegate,
    private val secureStorage: SecureStorage,
    private val userCodeRepository: UserCodeRepository?,
    private val reader: CardReader,
    private val jsonRpcConverter: JSONRPCConverter,
    private val preflightReadFilter: PreflightReadFilter?,
) {
    fun build(config: Config, cardId: String? = null, initialMessage: Message? = null): CardSession {
        return CardSession(
            cardId = cardId,
            viewDelegate = viewDelegate,
            environment = SessionEnvironment(config, secureStorage),
            userCodeRepository = userCodeRepository,
            reader = reader,
            jsonRpcConverter = jsonRpcConverter,
            secureStorage = secureStorage,
            initialMessage = initialMessage,
            preflightReadFilter = preflightReadFilter,
        )
    }
}
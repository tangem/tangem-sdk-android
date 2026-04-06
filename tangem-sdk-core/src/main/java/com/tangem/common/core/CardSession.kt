package com.tangem.common.core

import com.tangem.*
import com.tangem.common.*
import com.tangem.common.apdu.Apdu
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.encryption.EncryptionMode
import com.tangem.common.extensions.VoidCallback
import com.tangem.common.extensions.calculateSha256
import com.tangem.common.json.JSONRPCConverter
import com.tangem.common.json.JSONRPCLinker
import com.tangem.common.nfc.CardReader
import com.tangem.common.services.secure.CardAccessTokensRepository
import com.tangem.common.services.secure.SecureStorage
import com.tangem.common.usersCode.UserCodeRepository
import com.tangem.common.v8.SecureChannelSession
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
import com.tangem.operations.securechannel.authorize.AuthorizeWithPINTask
import com.tangem.operations.securechannel.establish.EstablishSecureChannelWithAccessTokenTask
import com.tangem.operations.securechannel.establish.EstablishSecureChannelWithSecurityDelayTask
import com.tangem.operations.securechannel.manageAccessTokens.ManageAccessTokensTask
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.PrintWriter
import java.io.StringWriter

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
    private val cardAccessTokensRepository: CardAccessTokensRepository?,
    private val reader: CardReader,
    private val jsonRpcConverter: JSONRPCConverter,
    private val secureStorage: SecureStorage,
    private var initialMessage: ViewDelegateMessage? = null,
) {

    var cardId: String? = cardId
        private set
    var connectedTag: TagType? = null
        private set
    var state = CardSessionState.Inactive
        private set

    var secureChannelSession: SecureChannelSession? = null
        private set

    private var resetCodesController: ResetCodesController? = null

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO) + CoroutineExceptionHandler { _, throwable ->
        handleScopeCoroutineException(throwable)
    }

    private var preflightReadMode: PreflightReadMode = PreflightReadMode.FullCardRead

    private var onTagConnectedAfterResume: VoidCallback? = null

    private var secureChannelRetryCount = 0

    /** Tag remembered for wrong-card detection during command execution */
    private var currentTag: TagType? = null

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
    ) {
        if (state != CardSessionState.Inactive) {
            val error = TangemSdkError.Busy()
            Log.error { "$error" }
            callback(CompletionResult.Failure(error))
            return
        }

        Log.session { "start card session with runnable" }
        prepareSession(runnable) { prepareResult ->
            when (prepareResult) {
                is CompletionResult.Success -> {
                    start(iconScanRes) { _, error ->
                        if (error != null) {
                            Log.error { "$error" }
                            callback(CompletionResult.Failure(error))
                            return@start
                        }
                        Log.session { "start runnable" }
                        runnable.run(this) { result ->
                            Log.session { "runnable completed" }
                            when (result) {
                                is CompletionResult.Success -> {
                                    saveAccessCodeIfNeeded()
                                    handleHealthIfNeeded {
                                        stop()
                                        callback(result)
                                    }
                                    return@run
                                }
                                is CompletionResult.Failure -> {
                                    Log.error { "${result.error}" }
                                    stopWithError(result.error, SessionErrorMoment.End)
                                }
                            }
                            callback(result)
                        }
                    }
                }
                is CompletionResult.Failure -> {
                    stopWithError(prepareResult.error, SessionErrorMoment.Preparation)
                    callback(CompletionResult.Failure(prepareResult.error))
                }
            }
        }
    }

    /**
     * Starts a card session and performs preflight [ReadCommand].
     * @param iconScanRes iconResource to replace on Scan Bottom Sheet
     * @param onSessionStarted: callback with the card session. Can contain [TangemSdkError] if something goes wrong.
     */
    fun start(iconScanRes: Int? = null, onSessionStarted: SessionStartedCallback) {
        Log.session { "start card session with delegate" }
        reader.scope = scope
        reader.startSession()

        state = CardSessionState.Active
        runBlocking {
            viewDelegate.onSessionStarted(
                cardId = cardId,
                message = initialMessage,
                enableHowTo = environment.config.howToIsEnabled,
                iconScanRes = iconScanRes,
                productType = environment.config.productType,
            )
        }

        scope.launch {
            reader.tag
                .filterNotNull()
                .take(1)
                .collect { tagType ->
                    selectApplet()
                        .doOnSuccess {
                            if (tagType == TagType.Nfc && preflightReadMode != PreflightReadMode.None) {
                                preflightCheck(onSessionStarted)
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
            reader.tag
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
            reader.tag
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
                        secureChannelSession?.reset()
                        connectedTag = null
                    } else if (it != null) {
                        connectedTag = it
                    }
                }
        }
    }

    fun resetAccessTokens() {
        Log.session { "try resetAccessTokens" }
        environment.cardAccessTokens = null
        val cardId = environment.card?.cardId ?: return
        if (cardId == environment.card?.cardId) {
            cardAccessTokensRepository?.deleteTokens(setOf(cardId))
        }
    }

    fun fetchAccessTokensIfNeeded() {
        Log.session { "Try fetch access tokens" }
        val card = environment.card ?: return
        val tokens = cardAccessTokensRepository?.fetch(cardId = card.cardId) ?: return

        environment.cardAccessTokens = tokens
        Log.session { "Access tokens fetched successfully" }
    }

    fun saveAccessTokensIfNeeded() {
        Log.session { "Try save access tokens" }
        val card = environment.card ?: return
        val tokens = environment.cardAccessTokens ?: return

        try {
            cardAccessTokensRepository?.save(tokens, card.cardId)
            Log.session { "Access tokens saved successfully" }
        } catch (error: Throwable) {
            Log.error { "Access tokens saving failed: $error" }
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
        val hasAccessCodes = when {
            userCodeRepository == null -> false
            cardId != null -> userCodeRepository.hasSavedUserCode(cardId!!)
            else -> userCodeRepository.hasSavedUserCodes()
        }

        val hasCardAccessTokens = when {
            cardAccessTokensRepository == null -> false
            cardId != null -> cardAccessTokensRepository.contains(cardId!!)
            else -> !cardAccessTokensRepository.isEmpty
        }

        return hasAccessCodes || hasCardAccessTokens
    }

    private fun <T : CardSessionRunnable<*>> prepareSession(runnable: T, callback: CompletionCallback<Unit>) {
        Log.session { "prepare card session" }
        preflightReadMode = runnable.preflightReadMode()
        environment.encryptionMode = runnable.encryptionMode

        Log.session { "User code policy is ${environment.config.userCodeRequestPolicy}" }
        Log.session { "Encryption mode is ${environment.encryptionMode}" }

        if (!runnable.allowsRequestAccessCodeFromRepository) {
            runnable.prepare(this, callback)
            return
        }

        val requestUserCode = { codeType: UserCodeType ->
            when (codeType) {
                UserCodeType.AccessCode -> {
                    environment.accessCode = UserCode(codeType, null)
                }
                UserCodeType.Passcode -> {
                    environment.passcode = UserCode(codeType, null)
                }
            }
            requestUserCodeIfNeeded(
                type = codeType,
                isFirstAttempt = true,
                showWelcomeBackWarning = false,
            ) { userCodeResult ->
                userCodeResult
                    .doOnSuccess { runnable.prepare(this, callback) }
                    .doOnFailure { error -> callback(CompletionResult.Failure(error)) }
            }
        }

        when (val policy = environment.config.userCodeRequestPolicy) {
            is UserCodeRequestPolicy.AlwaysWithBiometrics -> {
                scope.launch(Dispatchers.Main) {
                    if (shouldRequestBiometrics()) {
                        userCodeRepository?.unlock()
                            ?.doOnSuccess { runnable.prepare(this@CardSession, callback) }
                            ?.doOnFailure { e ->
                                Log.error {
                                    """
                                        User codes storage could not be unlocked
                                        |- Cause: $e
                                    """.trimIndent()
                                }
                                requestUserCode(policy.codeType)
                            }
                    } else {
                        requestUserCode(policy.codeType)
                    }
                }
            }
            is UserCodeRequestPolicy.Always -> {
                requestUserCode(policy.codeType)
            }
            is UserCodeRequestPolicy.Default -> {
                runnable.prepare(this, callback)
            }
        }
    }

    private fun preflightCheck(onSessionStarted: SessionStartedCallback) {
        Log.session { "start preflight check" }

        val preflightTask = PreflightReadTask(
            readMode = preflightReadMode,
            filter = preflightReadFilter ?: cardId?.let(::CardIdPreflightReadFilter),
            secureStorage = secureStorage,
        )

        preflightTask.run(this) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    onSessionStarted(this, null)
                }

                is CompletionResult.Failure -> {
                    val wrongType = when (result.error) {
                        is TangemSdkError.WrongCardType -> WrongValueType.CardType
                        is TangemSdkError.WrongCardNumber -> WrongValueType.CardId(result.error.cardId)
                        else -> null
                    }
                    if (wrongType != null) {
                        Log.error { "${result.error}" }
                        viewDelegate.onWrongCard(wrongType)
                        scope.launch(scope.coroutineContext) {
                            delay(timeMillis = 3500)
                            if (connectedTag == null) {
                                onSessionStarted(this@CardSession, result.error)
                                stopWithError(result.error, SessionErrorMoment.PreflightCheck)
                            } else {
                                viewDelegate.onTagConnected()
                                preflightCheck(onSessionStarted)
                            }
                        }
                    } else {
                        onSessionStarted(this, result.error)
                        stopWithError(result.error, SessionErrorMoment.PreflightCheck)
                    }
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
        cardAccessTokensRepository?.lock()
        resetSensitiveData()
    }

    private fun resetSensitiveData() {
        secureChannelSession?.reset()
        secureChannelSession = null
        environment.reset()
    }

    fun send(apdu: CommandApdu, callback: CompletionCallback<ResponseApdu>) {
        Log.session { "send CommandApdu" }
        scope.launch {
            val finalResult = reader.tag
                .filterNotNull()
                .filter { tag ->
                    // Wrong-card detection: compare current tag with remembered tag
                    val remembered = currentTag
                    if (remembered != null && tag != remembered) {
                        val formatter = CardIdFormatter(environment.config.cardIdDisplayFormat)
                        val formattedCardId = environment.card?.cardId?.let { formatter.getFormattedCardId(it) }
                        viewDelegate.onWrongCard(WrongValueType.CardId(formattedCardId))
                        false
                    } else {
                        true
                    }
                }
                .map { establishLegacyEncryptionIfNeeded() }
                .map {
                    apdu.encrypt(
                        environment.encryptionMode,
                        environment.encryptionKey,
                        secureChannelSession?.makeCommandAPDUNonce(),
                    )
                }
                .map { encryptedApdu -> reader.transceiveApdu(encryptedApdu) }
                .map { responseApdu ->
                    decrypt(
                        responseApdu,
                        secureChannelSession?.makeResponseAPDUNonce(),
                    )
                }
                .catch {
                    if (it is TangemSdkError) {
                        Log.error { "$it" }
                        callback(CompletionResult.Failure(it))
                    }
                }
                .transform { result ->
                    if (result is CompletionResult.Failure && result.error is TangemSdkError.TagLost) {
                        Log.session { "tag lost. Waiting for tag..." }
                    } else {
                        emit(result)
                    }
                }
                .first()
            when (finalResult) {
                is CompletionResult.Success -> callback(finalResult)
                is CompletionResult.Failure -> {
                    Log.error { "${finalResult.error}" }
                    callback(finalResult)
                }
            }
        }
    }

    fun pause() {
        reader.pauseSession()
    }

    fun resume(onTagConnected: VoidCallback? = null) {
        onTagConnectedAfterResume = onTagConnected
        reader.resumeSession()
    }

    // MARK: - Encryption

    /**
     * Establishes encryption if needed for the given card session encryption level.
     * Handles both legacy (CBC) and v8+ (CCM secure channel) encryption flows.
     *
     * @param cardSessionEncryption required encryption level for the command
     * @param shouldAskForAccessCode whether the session should handle access code prompting
     * @param callback completion callback
     */
    fun establishEncryptionIfNeeded(
        cardSessionEncryption: CardSessionEncryption,
        shouldAskForAccessCode: Boolean,
        callback: CompletionCallback<Unit>,
    ) {
        Log.session { "Try establish encryption for type: $cardSessionEncryption" }

        if (cardSessionEncryption == CardSessionEncryption.NONE) {
            Log.session { "Encryption is not needed for this command" }
            callback(CompletionResult.Success(Unit))
            return
        }

        secureChannelSession?.let { session ->
            if (!session.isElevationRequired(cardSessionEncryption)) {
                Log.session { "Encryption elevation is not needed" }
                callback(CompletionResult.Success(Unit))
                return
            }
        }

        val card = environment.card
        if (card == null) {
            Log.session { "Encryption can not be established because card is not read" }
            callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
            return
        }

        if (card.firmwareVersion < FirmwareVersion.v8) {
            establishLegacyEncryption(callback)
        } else {
            val needsAuthorizationWithAccessCode = checkIfAuthorizationWithAccessCodeIsNeeded(
                cardSessionEncryption = cardSessionEncryption,
                shouldAskForAccessCode = shouldAskForAccessCode,
            )

            // Request Access Code as early as possible
            if (needsAuthorizationWithAccessCode && environment.accessCode.isDefault()) {
                Log.session { "Access code is needed, request early" }
                environment.accessCode = UserCode(UserCodeType.AccessCode, null)
                pause()
                requestUserCodeIfNeeded(
                    type = UserCodeType.AccessCode,
                    isFirstAttempt = true,
                    showWelcomeBackWarning = false,
                ) { result ->
                    when (result) {
                        is CompletionResult.Success -> {
                            resume()
                            establishEncryptionIfNeeded(
                                cardSessionEncryption = cardSessionEncryption,
                                shouldAskForAccessCode = shouldAskForAccessCode,
                                callback = callback,
                            )
                        }
                        is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                    }
                }
                return
            }

            establishSecureChannel { result ->
                when (result) {
                    is CompletionResult.Success -> {
                        val session = secureChannelSession
                        if (session == null) {
                            Log.session { "Secure channel session not found" }
                            callback(CompletionResult.Failure(TangemSdkError.NeedEncryption()))
                            return@establishSecureChannel
                        }

                        Log.session { "SecureChannel established successfully. Current access level is ${session.accessLevel}" }

                        if (!needsAuthorizationWithAccessCode && !session.isElevationRequired(cardSessionEncryption)) {
                            Log.session { "Authorization with PIN is not needed" }
                            callback(CompletionResult.Success(Unit))
                            return@establishSecureChannel
                        }

                        Log.session { "Authorization with PIN is needed" }
                        AuthorizeWithPINTask().run(this) { authorizeResult ->
                            when (authorizeResult) {
                                is CompletionResult.Success -> {
                                    requestCardAccessTokensIfNeeded(callback)
                                }
                                is CompletionResult.Failure -> {
                                    when (authorizeResult.error) {
                                        is TangemSdkError.AccessCodeRequired -> {
                                            handleWrongUserCode(UserCodeType.AccessCode) { pinResult ->
                                                when (pinResult) {
                                                    is CompletionResult.Success -> {
                                                        establishEncryptionIfNeeded(
                                                            cardSessionEncryption = cardSessionEncryption,
                                                            shouldAskForAccessCode = shouldAskForAccessCode,
                                                            callback = callback,
                                                        )
                                                    }
                                                    is CompletionResult.Failure -> {
                                                        callback(CompletionResult.Failure(pinResult.error))
                                                    }
                                                }
                                            }
                                        }
                                        else -> callback(CompletionResult.Failure(authorizeResult.error))
                                    }
                                }
                            }
                        }
                    }
                    is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                }
            }
        }
    }

    private fun establishLegacyEncryption(callback: CompletionCallback<Unit>) {
        if (environment.encryptionMode == EncryptionMode.None || environment.encryptionKey != null) {
            Log.session { "Legacy encryption is not needed or already established" }
            callback(CompletionResult.Success(Unit))
            return
        }

        Log.session { "Try establish legacy encryption" }

        val encryptionHelper = EncryptionHelper.create(environment.encryptionMode)
        if (encryptionHelper == null) {
            callback(CompletionResult.Failure(TangemSdkError.CryptoUtilsError("Failed to establish encryption")))
            return
        }

        scope.launch {
            val openSessionCommand = OpenSessionCommand(encryptionHelper.keyA)
            val apdu = openSessionCommand.serialize(environment)

            when (val response = reader.transceiveApdu(apdu)) {
                is CompletionResult.Success -> {
                    try {
                        val result = openSessionCommand.deserialize(environment, response.data)
                        val uid = result.uid
                        val accessCode = environment.accessCode.value
                            ?: throw TangemSdkError.AccessCodeRequired()

                        val protocolKey = accessCode.pbkdf2Hash(uid, iterations = 50)
                        val secret = encryptionHelper.generateSecret(result.sessionKeyB)
                        val sessionKey = (secret + protocolKey).calculateSha256()
                        environment.encryptionKey = sessionKey
                        Log.session { "The legacy encryption established" }
                        callback(CompletionResult.Success(Unit))
                    } catch (error: Throwable) {
                        val sdkError = if (error is TangemSdkError) error
                        else TangemSdkError.CryptoUtilsError("Failed to establish encryption")
                        callback(CompletionResult.Failure(sdkError))
                    }
                }
                is CompletionResult.Failure -> callback(CompletionResult.Failure(response.error))
            }
        }
    }

    private fun establishSecureChannel(callback: CompletionCallback<Unit>) {
        Log.session { "Establish secure channel" }

        val card = environment.card
        if (card == null) {
            Log.session { "Encryption can not be established because card is not read" }
            callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
            return
        }

        secureChannelSession = SecureChannelSession(card.cardId)
        environment.encryptionMode = EncryptionMode.None
        environment.encryptionKey = null

        if (environment.cardAccessTokens == null) {
            EstablishSecureChannelWithSecurityDelayTask().run(this) { result ->
                when (result) {
                    is CompletionResult.Success -> {
                        secureChannelRetryCount = 0
                        callback(CompletionResult.Success(Unit))
                    }
                    is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                }
            }
        } else {
            EstablishSecureChannelWithAccessTokenTask().run(this) { result ->
                when (result) {
                    is CompletionResult.Success -> {
                        secureChannelRetryCount = 0
                        callback(CompletionResult.Success(Unit))
                    }
                    is CompletionResult.Failure -> {
                        if (result.error is TangemSdkError.InvalidAccessTokens &&
                            secureChannelRetryCount < MAX_SECURE_CHANNEL_RETRIES
                        ) {
                            secureChannelRetryCount++
                            establishSecureChannel(callback)
                        } else {
                            callback(CompletionResult.Failure(result.error))
                        }
                    }
                }
            }
        }
    }

    private fun checkIfAuthorizationWithAccessCodeIsNeeded(
        cardSessionEncryption: CardSessionEncryption,
        shouldAskForAccessCode: Boolean,
    ): Boolean {
        // Some commands handle PIN by themselves
        if (!shouldAskForAccessCode) return false

        // Authorization is mandatory by command
        if (cardSessionEncryption == CardSessionEncryption.SECURE_CHANNEL_WITH_PIN &&
            environment.card?.isAccessCodeSet == true
        ) {
            return true
        }

        // Authorization is mandatory by user settings
        if (environment.card?.userSettings?.isPINRequired == true) return true

        // Access code is set, no access tokens → PIN must be entered
        if (environment.card?.isAccessCodeSet == true && environment.cardAccessTokens == null) return true

        return false
    }

    private fun requestCardAccessTokensIfNeeded(callback: CompletionCallback<Unit>) {
        if (environment.cardAccessTokens != null) {
            callback(CompletionResult.Success(Unit))
            return
        }

        val card = environment.card
        if (card != null && card.settings.isBackupRequired && card.backupStatus?.isActive == false) {
            callback(CompletionResult.Success(Unit))
            return
        }

        ManageAccessTokensTask().run(this, callback)
    }

    // MARK: - Wrong user code handling

    fun handleWrongUserCode(type: UserCodeType, callback: CompletionCallback<Unit>) {
        val sdkError = TangemSdkError.from(type, environment)

        when (sdkError) {
            is TangemSdkError.AccessCodeRequired, is TangemSdkError.PasscodeRequired -> {
                pause()
                viewDelegate.setMessage(Message(body = sdkError.customMessage))
            }
            else -> pause()
        }

        when (type) {
            UserCodeType.AccessCode -> environment.accessCode = UserCode(UserCodeType.AccessCode, null)
            UserCodeType.Passcode -> environment.passcode = UserCode(UserCodeType.Passcode, null)
        }

        requestUserCodeIfNeeded(
            type = type,
            isFirstAttempt = false,
            showWelcomeBackWarning = false,
        ) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    resume()
                    callback(CompletionResult.Success(Unit))
                }
                is CompletionResult.Failure -> {
                    releaseTag()
                    callback(CompletionResult.Failure(result.error))
                }
            }
        }
    }

    // MARK: - Health

    fun handleHealthIfNeeded(completion: () -> Unit) {
        val config = environment.config
        if (!config.handleHealth || environment.card?.hasHealthReport != true) {
            completion()
            return
        }

        Log.session { "Show health alert" }
        pause()
        viewDelegate.showHealthAlert(onContinue = completion)
    }

    // MARK: - Tag management

    /** Remember the current connected tag for wrong-card detection during command execution */
    fun rememberTag() {
        currentTag = connectedTag
    }

    /** Release the remembered tag after command execution */
    fun releaseTag() {
        currentTag = null
    }

    // MARK: - Access code management

    fun saveAccessCodeIfNeeded() {
        val card = environment.card ?: return
        // v8+ uses access tokens instead of saving access codes
        if (card.firmwareVersion >= FirmwareVersion.v8) return
        if (environment.accessCode.value == null) return

        Log.session { "Try save an access code" }
        @Suppress("GlobalCoroutineUsage")
        GlobalScope.launch {
            userCodeRepository?.save(card.cardId, environment.accessCode)
                ?.doOnResult { userCodeRepository.lock() }
                ?.doOnFailure { Log.error { "Access code saving failed: $it" } }
        }
    }

    private suspend fun selectApplet(): CompletionResult<ByteArray?> {
        Log.session { "select the Wallet applet" }
        return reader.transceiveRaw(Apdu.build(Apdu.SELECT, Apdu.TANGEM_WALLET_AID))
    }

    private suspend fun establishLegacyEncryptionIfNeeded(): CompletionResult<Boolean> {
        Log.session { "establish legacy encryption if needed" }
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

    private fun decrypt(
        result: CompletionResult<ResponseApdu>,
        nonce: ByteArray? = null,
    ): CompletionResult<ResponseApdu> {
        return when (result) {
            is CompletionResult.Success -> {
                try {
                    CompletionResult.Success(
                        result.data.decrypt(environment.encryptionMode, environment.encryptionKey, nonce),
                    )
                } catch (error: TangemSdkError) {
                    CompletionResult.Failure(error)
                }
            }
            is CompletionResult.Failure -> result
        }
    }

    fun requestUserCodeIfNeeded(
        type: UserCodeType,
        isFirstAttempt: Boolean,
        showWelcomeBackWarning: Boolean,
        callback: CompletionCallback<Unit>,
    ) {
        if (showWelcomeBackWarning) {
            Log.session { "showWelcomeBackWarning" }
            viewDelegate.showWelcomeBackWarning { result ->
                when (result) {
                    is CompletionResult.Success -> {
                        Log.session { "showWelcomeBackWarning success" }
                        val showForgotButton = environment.card?.backupStatus?.isActive ?: false
                        val cardId = environment.card?.cardId ?: this.cardId
                        val formattedCardId = cardId?.let {
                            CardIdFormatter(environment.config.cardIdDisplayFormat).getFormattedCardId(it)
                        }
                        showRequestAccessCode(
                            type = type,
                            isFirstAttempt = isFirstAttempt,
                            showForgotButton = showForgotButton,
                            formattedCardId = formattedCardId,
                            callback = callback,
                        )
                    }
                    is CompletionResult.Failure -> {
                        Log.session { "showWelcomeBackWarning error" }
                        callback(CompletionResult.Failure(result.error))
                    }
                }
            }
        } else {
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

            showRequestAccessCode(
                type = type,
                isFirstAttempt = isFirstAttempt,
                showForgotButton = showForgotButton,
                formattedCardId = formattedCardId,
                callback = callback,
            )
        }
    }

    private fun showRequestAccessCode(
        type: UserCodeType,
        isFirstAttempt: Boolean,
        showForgotButton: Boolean,
        formattedCardId: String?,
        callback: CompletionCallback<Unit>,
    ) {
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

    companion object {
        const val MAX_SECURE_CHANNEL_RETRIES = 5
    }
}

typealias SessionStartedCallback = (session: CardSession, error: TangemError?) -> Unit

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
            cardAccessTokensRepository = CardAccessTokensRepository(),
        )
    }
}
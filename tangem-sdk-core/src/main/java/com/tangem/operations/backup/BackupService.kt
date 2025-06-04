package com.tangem.operations.backup

import com.squareup.moshi.JsonClass
import com.tangem.Log
import com.tangem.Message
import com.tangem.TangemSdk
import com.tangem.common.CardIdFormatter
import com.tangem.common.CompletionResult
import com.tangem.common.StringsLocator
import com.tangem.common.UserCodeType
import com.tangem.common.card.Card
import com.tangem.common.card.EllipticCurve
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.ProductType
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.calculateSha256
import com.tangem.common.extensions.guard
import com.tangem.common.json.MoshiJsonConverter
import com.tangem.common.services.secure.SecureStorage
import com.tangem.operations.CommandResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.PrintWriter
import java.io.StringWriter

@Suppress("LargeClass")
@OptIn(ExperimentalCoroutinesApi::class)
class BackupService(
    private val sdk: TangemSdk,
    private var repo: BackupRepo,
    private val stringsLocator: StringsLocator,
) {
    var currentState: State = State.Preparing
        private set(value) {
            _currentStateFlow.value = value
            field = value
        }

    private var _currentStateFlow: MutableStateFlow<State> = MutableStateFlow(State.Preparing)
    val currentStateAsFlow: StateFlow<State> get() = _currentStateFlow

    val canAddBackupCards: Boolean
        get() = addedBackupCardsCount < MAX_BACKUP_CARDS_COUNT && repo.data.primaryCard?.linkingKey != null

    val hasIncompletedBackup: Boolean
        get() = when (currentState) {
            is State.FinalizingPrimaryCard,
            is State.FinalizingBackupCard,
            -> true

            else -> false
        }

    val addedBackupCardsCount: Int get() = repo.data.backupCards.size

    val canProceed: Boolean
        get() = currentState != State.Preparing && currentState != State.Finished
    val accessCodeIsSet: Boolean get() = repo.data.accessCode != null
    val passcodeIsSet: Boolean get() = repo.data.passcode != null
    val primaryCardIsSet: Boolean get() = repo.data.primaryCard != null
    val primaryCardId: String? get() = repo.data.primaryCard?.cardId
    val primaryPublicKey: ByteArray? get() = repo.data.primaryCard?.cardPublicKey
    val backupCardIds: List<String> get() = repo.data.backupCards.map { it.cardId }
    val primaryCardBatchId: String? get() = repo.data.primaryCard?.batchId
    val backupCardsBatchIds: List<String> get() = repo.data.backupCards.mapNotNull(BackupCard::batchId)

    /**
     * Perform additional compatibility checks while adding backup cards. Change this setting only if you understand what you do.
     */
    var skipCompatibilityChecks: Boolean = false

    private val backupScope = CoroutineScope(Dispatchers.IO) + CoroutineExceptionHandler { _, throwable ->
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val exceptionAsString: String = sw.toString()
        Log.error { exceptionAsString }
    }

    private val handleErrors = sdk.config.handleErrors
    private val backupCertificateProvider by lazy {
        BackupCertificateProvider(
            secureStorage = sdk.secureStorage,
            isNewAttestationEnabled = sdk.config.isNewOnlineAttestationEnabled,
            isTangemAttestationProdEnv = sdk.config.isTangemAttestationProdEnv,
        )
    }

    init {
        updateState()
    }

    fun discardSavedBackup() {
        repo.reset()
        updateState()
    }

    fun addBackupCard(callback: CompletionCallback<Card>) {
        val primaryCard = repo.data.primaryCard.guard {
            callback(CompletionResult.Failure(TangemSdkError.MissingPrimaryCard()))
            return
        }
        if (handleErrors && addedBackupCardsCount >= MAX_BACKUP_CARDS_COUNT) {
            callback(CompletionResult.Failure(TangemSdkError.TooMuchBackupCards()))
            return
        }

        if (primaryCard.certificate != null) {
            readBackupCard(primaryCard, callback)
            return
        }

        val primaryCardFirmwareVersion = primaryCard.firmwareVersion
        if (primaryCardFirmwareVersion == null) {
            callback(CompletionResult.Failure(TangemSdkError.MissingPrimaryCard()))
            return
        }

        val primaryCardManufacturerName = primaryCard.manufacturer?.name
        if (primaryCardManufacturerName == null) {
            callback(CompletionResult.Failure(TangemSdkError.MissingPrimaryCard()))
            return
        }

        backupScope.launch {
            fetchCertificate(
                cardId = primaryCard.cardId,
                cardPublicKey = primaryCard.cardPublicKey,
                issuerPublicKey = primaryCard.issuer.publicKey,
                manufacturerName = primaryCardManufacturerName,
                firmwareVersion = primaryCardFirmwareVersion,
                onFailed = { callback(CompletionResult.Failure(it)) },
            ) {
                val updatedPrimaryCard = primaryCard.copy(certificate = it)
                repo.data = repo.data.copy(primaryCard = updatedPrimaryCard)
                readBackupCard(updatedPrimaryCard, callback)
            }
        }
    }

    fun setAccessCode(code: String): CompletionResult<Unit> {
        repo.data = repo.data.copy(accessCode = null)

        if (handleErrors) {
            if (code.isEmpty()) {
                return CompletionResult.Failure(TangemSdkError.AccessCodeRequired())
            }
            if (code == UserCodeType.AccessCode.defaultValue) {
                return CompletionResult.Failure(TangemSdkError.AccessCodeCannotBeChanged())
            }
        }

        if (currentState != State.Preparing && currentState != State.FinalizingPrimaryCard) {
            return CompletionResult.Failure(TangemSdkError.AccessCodeCannotBeChanged())
        }
        repo.data = repo.data.copy(accessCode = code.calculateSha256())
        updateState()
        return CompletionResult.Success(Unit)
    }

    fun setPasscode(code: String): CompletionResult<Unit> {
        repo.data = repo.data.copy(passcode = null)

        if (handleErrors) {
            if (code.isEmpty()) {
                return CompletionResult.Failure(TangemSdkError.PasscodeRequired())
            }
            if (code == UserCodeType.Passcode.defaultValue) {
                return CompletionResult.Failure(TangemSdkError.PasscodeCannotBeChanged())
            }
        }

        if (currentState != State.Preparing || currentState != State.FinalizingPrimaryCard) {
            return CompletionResult.Failure(TangemSdkError.PasscodeCannotBeChanged())
        }
        repo.data = repo.data.copy(passcode = code.calculateSha256())
        updateState()
        return CompletionResult.Success(Unit)
    }

    fun proceedBackup(iconScanRes: Int? = null, callback: CompletionCallback<Card>) {
        when (val currentState = currentState) {
            State.FinalizingPrimaryCard ->
                handleFinalizePrimaryCard(iconScanRes = iconScanRes) { result -> handleCompletion(result, callback) }

            is State.FinalizingBackupCard ->
                handleWriteBackupCard(currentState.index, iconScanRes) { result ->
                    handleCompletion(result, callback)
                }

            State.Preparing, State.Finished ->
                callback(CompletionResult.Failure(TangemSdkError.BackupServiceInvalidState()))
        }
    }

    fun setPrimaryCard(primaryCard: PrimaryCard) {
        repo.data = repo.data.copy(primaryCard = primaryCard)
        updateState()
    }

    fun readPrimaryCard(iconScanRes: Int? = null, cardId: String? = null, callback: CompletionCallback<Unit>) {
        val formattedCardId = cardId?.let { CardIdFormatter(sdk.config.cardIdDisplayFormat).getFormattedCardId(it) }

        val message = formattedCardId?.let {
            Message(
                header = null,
                body = stringsLocator.getString(StringsLocator.ID.BACKUP_PREPARE_PRIMARY_CARD_MESSAGE_FORMAT, it),
            )
        } ?: Message(header = stringsLocator.getString(StringsLocator.ID.BACKUP_PREPARE_PRIMARY_CARD_MESSAGE))

        sdk.startSessionWithRunnable(
            runnable = StartPrimaryCardLinkingCommand(),
            cardId = cardId,
            initialMessage = message,
            iconScanRes = iconScanRes,
        ) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    setPrimaryCard(result.data)
                    callback(CompletionResult.Success(Unit))
                }

                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }
        }
    }

    @Suppress("LongParameterList")
    private suspend fun fetchCertificate(
        cardId: String,
        cardPublicKey: ByteArray,
        issuerPublicKey: ByteArray,
        manufacturerName: String,
        firmwareVersion: FirmwareVersion,
        onFailed: (TangemSdkError) -> Unit,
        onLoaded: (ByteArray) -> Unit,
    ) {
        backupCertificateProvider.getCertificate(
            cardId = cardId,
            cardPublicKey = cardPublicKey,
            issuerPublicKey = issuerPublicKey,
            manufacturerName = manufacturerName,
            firmwareVersion = firmwareVersion,
        ) {
            val certificate = when (it) {
                is CompletionResult.Success -> it.data
                is CompletionResult.Failure -> {
                    onFailed(TangemSdkError.IssuerSignatureLoadingFailed())
                    return@getCertificate
                }
            }
            onLoaded(certificate)
        }
    }

    private fun handleCompletion(result: CompletionResult<Card>, callback: CompletionCallback<Card>) {
        when (result) {
            is CompletionResult.Success -> {
                updateState()
                callback(result)
            }

            is CompletionResult.Failure -> callback(result)
        }
    }

    private fun updateState(): State {
        val preparingStateCondition =
            repo.data.accessCode == null || repo.data.primaryCard == null || repo.data.backupCards.isEmpty()
        val finalizingStateCondition =
            repo.data.attestSignature == null ||
                repo.data.backupData.size < repo.data.backupCards.size ||
                repo.data.primaryCardFinalized == false
        val finalizingStateConditionWithCount = repo.data.finalizedBackupCardsCount < repo.data.backupCards.size
        currentState = when {
            preparingStateCondition -> State.Preparing
            finalizingStateCondition -> State.FinalizingPrimaryCard
            finalizingStateConditionWithCount -> State.FinalizingBackupCard(repo.data.finalizedBackupCardsCount + 1)

            else -> {
                onBackupCompleted()
                State.Finished
            }
        }
        return currentState
    }

    private fun addBackupCard(
        backupCardResponse: StartBackupCardLinkingTaskResponse,
        callback: CompletionCallback<Card>,
    ) {
        backupScope.launch {
            val backupCard = backupCardResponse.backupCard
            fetchCertificate(
                cardId = backupCard.cardId,
                cardPublicKey = backupCard.cardPublicKey,
                issuerPublicKey = backupCardResponse.card.issuer.publicKey,
                manufacturerName = backupCardResponse.card.manufacturer.name,
                firmwareVersion = backupCardResponse.card.firmwareVersion,
                onFailed = { callback(CompletionResult.Failure(TangemSdkError.IssuerSignatureLoadingFailed())) },
            ) { cert ->
                val updatedBackupCard = backupCard.copy(certificate = cert)
                val updatedList = repo.data.backupCards
                    .filter { it.cardId != updatedBackupCard.cardId }
                    .plus(updatedBackupCard)
                repo.data = repo.data.copy(backupCards = updatedList)
                updateState()
                callback(CompletionResult.Success(backupCardResponse.card))
            }
        }
    }

    private fun readBackupCard(primaryCard: PrimaryCard, callback: CompletionCallback<Card>) {
        sdk.startSessionWithRunnable(
            runnable = StartBackupCardLinkingTask(
                primaryCard = primaryCard,
                addedBackupCards = repo.data.backupCards.map { it.cardId },
                skipCompatibilityChecks = skipCompatibilityChecks,
            ),
            initialMessage = Message(
                header = stringsLocator.getString(StringsLocator.ID.BACKUP_ADD_BACKUP_CARD_MESSAGE),
            ),
        ) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    addBackupCard(result.data, callback)
                }

                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }
        }
    }

    private fun handleFinalizePrimaryCard(iconScanRes: Int? = null, callback: CompletionCallback<Card>) {
        try {
            if (handleErrors && repo.data.accessCode == null && repo.data.passcode == null) {
                throw TangemSdkError.AccessCodeOrPasscodeRequired()
            }
            val accessCode = repo.data.accessCode ?: UserCodeType.AccessCode.defaultValue.calculateSha256()
            val passcode = repo.data.passcode ?: UserCodeType.Passcode.defaultValue.calculateSha256()
            val primaryCard = repo.data.primaryCard.guard { throw TangemSdkError.MissingPrimaryCard() }

//            val linkableBackupCards = repo.data.backupCards.map { card ->
//                val certificate = repo.data.certificates[card.cardId]
//                    .guard { throw TangemSdkError.CertificateSignatureRequired() }
//                card.makeLinkable(certificate)
//            }

            if (handleErrors) {
                if (repo.data.backupCards.isEmpty()) throw TangemSdkError.EmptyBackupCards()
                if (repo.data.backupCards.size > MAX_BACKUP_CARDS_COUNT) throw TangemSdkError.TooMuchBackupCards()
            }

            val task = FinalizePrimaryCardTask(
                backupCards = repo.data.backupCards,
                accessCode = accessCode,
                passcode = passcode,
                readBackupStartIndex = repo.data.backupData.size,
                attestSignature = repo.data.attestSignature,
                onLink = {
                    repo.data = repo.data.copy(attestSignature = it, primaryCardFinalized = false)
                },
                onRead = { cardId, data ->
                    repo.data = repo.data.copy(
                        backupData = repo.data.backupData.plus(Pair(cardId, data)),
                        primaryCardFinalized = false,
                    )
                },
                onFinalize = {
                    repo.data = repo.data.copy(primaryCardFinalized = true)
                },
            )

            val message = if (sdk.config.productType == ProductType.RING) {
                Message(
                    header = null,
                    body = stringsLocator.getString(StringsLocator.ID.BACKUP_FINALIZE_PRIMARY_RING_MESSAGE),
                )
            } else {
                val formattedCardId = CardIdFormatter(style = sdk.config.cardIdDisplayFormat)
                    .getFormattedCardId(primaryCard.cardId)

                formattedCardId?.let {
                    Message(
                        header = null,
                        body = stringsLocator.getString(
                            StringsLocator.ID.BACKUP_FINALIZE_PRIMARY_CARD_MESSAGE_FORMAT,
                            it,
                        ),
                    )
                }
            }

            sdk.startSessionWithRunnable(
                runnable = task,
                cardId = primaryCard.cardId,
                initialMessage = message,
                iconScanRes = iconScanRes,
                callback = callback,
            )
        } catch (error: TangemSdkError) {
            callback(CompletionResult.Failure(error))
            return
        }
    }

    @Suppress("LongMethod")
    private fun handleWriteBackupCard(index: Int, iconScanRes: Int?, callback: CompletionCallback<Card>) {
        try {
            if (handleErrors && repo.data.accessCode == null && repo.data.passcode == null) {
                throw TangemSdkError.AccessCodeOrPasscodeRequired()
            }
            val accessCode =
                repo.data.accessCode ?: UserCodeType.AccessCode.defaultValue.calculateSha256()
            val passcode =
                repo.data.passcode ?: UserCodeType.Passcode.defaultValue.calculateSha256()

            val attestSignature =
                repo.data.attestSignature.guard { throw TangemSdkError.MissingPrimaryAttestSignature() }
            val primaryCard =
                repo.data.primaryCard.guard { throw TangemSdkError.MissingPrimaryCard() }

            val cardIndex = index - 1

            if (handleErrors && repo.data.backupCards.size > MAX_BACKUP_CARDS_COUNT) {
                throw TangemSdkError.TooMuchBackupCards()
            }

            val backupCards = repo.data.backupCards
            val backupCard = backupCards.getOrNull(cardIndex).guard {
                throw TangemSdkError.NoBackupCardForIndex()
            }
            val backupData = repo.data.backupData[backupCard.cardId].guard {
                throw TangemSdkError.NoBackupDataForCard()
            }

            val task = FinalizeBackupCardTask(
                primaryCard = primaryCard,
                backupCards = backupCards,
                backupData = backupData,
                attestSignature = attestSignature,
                accessCode = accessCode,
                passcode = passcode,
            )

            val message = if (sdk.config.productType == ProductType.RING) {
                Message(
                    header = null,
                    body = stringsLocator.getString(StringsLocator.ID.BACKUP_FINALIZE_BACKUP_RING_MESSAGE),
                )
            } else {
                val formattedCardId = CardIdFormatter(style = sdk.config.cardIdDisplayFormat)
                    .getFormattedCardId(backupCard.cardId)

                formattedCardId?.let {
                    Message(
                        header = null,
                        body = stringsLocator.getString(
                            StringsLocator.ID.BACKUP_FINALIZE_BACKUP_CARD_MESSAGE_FORMAT,
                            it,
                        ),
                    )
                }
            }

            sdk.startSessionWithRunnable(
                runnable = task,
                cardId = backupCard.cardId,
                initialMessage = message,
                iconScanRes = iconScanRes,
            ) { result ->
                when (result) {
                    is CompletionResult.Success -> {
                        repo.data = repo.data.copy(
                            finalizedBackupCardsCount = repo.data.finalizedBackupCardsCount + 1,
                        )
                        callback(CompletionResult.Success(result.data))
                    }

                    is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                }
            }
        } catch (error: TangemSdkError) {
            callback(CompletionResult.Failure(error))
            return
        }
    }

    private fun onBackupCompleted() {
        repo.reset()
        backupScope.coroutineContext.cancelChildren()
    }

    sealed class State {
        object Preparing : State()
        object FinalizingPrimaryCard : State()
        data class FinalizingBackupCard(val index: Int) : State()
        object Finished : State()
    }

    companion object {
        const val MAX_BACKUP_CARDS_COUNT = 2
    }
}

@Suppress("LongParameterList")
@JsonClass(generateAdapter = true)
class RawPrimaryCard(
    val cardId: String,
    val cardPublicKey: ByteArray,
    val linkingKey: ByteArray,
    // For compatibility check with backup card
    val existingWalletsCount: Int,
    val isHDWalletAllowed: Boolean,
    val issuer: Card.Issuer,
    val walletCurves: List<EllipticCurve>,
    val batchId: String?, // for compatibility with interrupted backups
    val firmwareVersion: FirmwareVersion?, // for compatibility with interrupted backups
    val isKeysImportAllowed: Boolean?, // for compatibility with interrupted backups
) : CommandResponse

@Suppress("LongParameterList")
@JsonClass(generateAdapter = true)
data class PrimaryCard(
    val cardId: String,
    val cardPublicKey: ByteArray,
    val linkingKey: ByteArray,
    // For compatibility check with backup card
    val existingWalletsCount: Int,
    val isHDWalletAllowed: Boolean,
    val issuer: Card.Issuer,
    val manufacturer: Card.Manufacturer?,
    val walletCurves: List<EllipticCurve>,
    val batchId: String?, // for compatibility with interrupted backups
    val firmwareVersion: FirmwareVersion?, // for compatibility with interrupted backups
    val isKeysImportAllowed: Boolean?, // for compatibility with interrupted backups
    val certificate: ByteArray?,
) : CommandResponse

@JsonClass(generateAdapter = true)
class RawBackupCard(
    val cardId: String,
    val cardPublicKey: ByteArray,
    val linkingKey: ByteArray,
    val attestSignature: ByteArray,
) : CommandResponse

@JsonClass(generateAdapter = true)
data class BackupCard(
    val cardId: String,
    val cardPublicKey: ByteArray,
    val linkingKey: ByteArray,
    val attestSignature: ByteArray,
    val firmwareVersion: FirmwareVersion? = null,
    val certificate: ByteArray?,
    val batchId: String? = null,
) : CommandResponse {
    constructor(rawBackupCard: RawBackupCard, certificate: ByteArray) : this(
        cardId = rawBackupCard.cardId,
        cardPublicKey = rawBackupCard.cardPublicKey,
        linkingKey = rawBackupCard.linkingKey,
        attestSignature = rawBackupCard.attestSignature,
        certificate = certificate,
    )
}

class EncryptedBackupData(val data: ByteArray, val salt: ByteArray)

@JsonClass(generateAdapter = true)
data class BackupServiceData(
    val accessCode: ByteArray? = null,
    val passcode: ByteArray? = null,
    val primaryCard: PrimaryCard? = null,
    val attestSignature: ByteArray? = null,
    val backupCards: List<BackupCard> = emptyList(),
    val certificates: Map<String, ByteArray> = emptyMap(),
    val backupData: Map<String, List<EncryptedBackupData>> = emptyMap(),
    val finalizedBackupCardsCount: Int = 0,
    val primaryCardFinalized: Boolean? = null,
) {
    val shouldSave: Boolean
        get() = attestSignature != null || backupData.isNotEmpty()
}

class BackupRepo(
    private val storage: SecureStorage,
    private val jsonConverter: MoshiJsonConverter,
) {

    private var isFetching: Boolean = false

    var data: BackupServiceData = BackupServiceData()
        set(value) {
            field = value
            save()
        }

    init {
        fetch()
    }

    fun reset() {
        storage.delete(account = StorageKey.BackupData.name)
        data = BackupServiceData()
    }

    private fun save() {
        if (isFetching || !data.shouldSave) return

        val encoded = jsonConverter.toJson(data).toByteArray()
        storage.store(data = encoded, account = StorageKey.BackupData.name)
    }

    private fun fetch() {
        isFetching = true

        val savedData = storage.get(account = StorageKey.BackupData.name)?.let { String(it) }
        if (savedData != null) {
            jsonConverter.fromJson<BackupServiceData>(savedData)?.let { decodedData ->
                data = decodedData
            }
        }
        isFetching = false
    }

    enum class StorageKey { BackupData }
}

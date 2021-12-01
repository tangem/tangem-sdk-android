package com.tangem.operations.backup

import com.squareup.moshi.JsonClass
import com.tangem.Message
import com.tangem.TangemSdk
import com.tangem.common.CardIdFormatter
import com.tangem.common.CompletionResult
import com.tangem.common.StringsLocator
import com.tangem.common.UserCodeType
import com.tangem.common.card.Card
import com.tangem.common.card.EllipticCurve
import com.tangem.common.core.CardIdDisplayFormat
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.calculateSha256
import com.tangem.common.extensions.guard
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.json.MoshiJsonConverter
import com.tangem.common.services.secure.SecureStorage
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvTag
import com.tangem.crypto.sign
import com.tangem.operations.CommandResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
        get() = addedBackupCardsCount < MAX_BACKUP_CARDS_COUNT &&
                repo.data.primaryCard?.linkingKey != null

    val hasIncompletedBackup: Boolean
        get() = when (currentState) {
            is State.FinalizingPrimaryCard,
            is State.FinalizingBackupCard,
            -> true
            else -> false
        }

    val addedBackupCardsCount: Int get() = repo.data.backupCards.size
    val canProceed: Boolean
        get() =
            (currentState != State.Preparing) &&
                    (currentState != State.Finished)
    val accessCodeIsSet: Boolean get() = repo.data.accessCode != null
    val passcodeIsSet: Boolean get() = repo.data.passcode != null
    val primaryCardIsSet: Boolean get() = repo.data.primaryCard != null
    val primaryCardId: String? get() = repo.data.primaryCard?.cardId
    val backupCardIds: List<String> get() = repo.data.backupCards.map { it.cardId }

    private val handleErrors = sdk.config.handleErrors

    init {
        updateState()
    }

    fun discardSavedBackup() {
        repo.reset()
        updateState()
    }

    fun addBackupCard(callback: CompletionCallback<Unit>) {
        val primaryCard = repo.data.primaryCard.guard {
            callback(CompletionResult.Failure(TangemSdkError.MissingPrimaryCard()))
            return
        }
        if (handleErrors) {
            if (addedBackupCardsCount >= MAX_BACKUP_CARDS_COUNT) {
                callback(CompletionResult.Failure(TangemSdkError.TooMuchBackupCards()))
                return
            }
        }
        readBackupCard(primaryCard, callback)
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

    fun proceedBackup(callback: CompletionCallback<Card>) {
        when (val currentState = currentState) {
            State.FinalizingPrimaryCard ->
                handleFinalizePrimaryCard { result -> handleCompletion(result, callback) }
            is State.FinalizingBackupCard ->
                handleWriteBackupCard(currentState.index) { result ->
                    handleCompletion(result, callback)
                }

            State.Preparing, State.Finished ->
                callback(CompletionResult.Failure(TangemSdkError.BackupServiceInvalidState()))
        }
    }

    fun setPrimaryCard(primaryCard: PrimaryCard) {
        repo.data = repo.data.copy(primaryCard = primaryCard)
        updateState()

        fetchCertificate(primaryCard.cardId, primaryCard.cardPublicKey)
    }

    fun readPrimaryCard(cardId: String? = null, callback: CompletionCallback<Unit>) {
        val message = cardId?.let {
            Message(
                header = null,
                body = stringsLocator.getString(
                    StringsLocator.ID.backup_prepare_primary_card_message_format,
                    cardId
                )
            )
        } ?: Message(
            header = stringsLocator.getString(
                StringsLocator.ID.backup_prepare_primary_card_message
            )
        )


        sdk.startSessionWithRunnable(
            runnable = StartPrimaryCardLinkingCommand(),
            cardId = cardId,
            initialMessage = message
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

    private fun handleCompletion(
        result: CompletionResult<Card>,
        callback: CompletionCallback<Card>,
    ) {
        when (result) {
            is CompletionResult.Success -> {
                updateState()
                callback(result)
            }
            is CompletionResult.Failure -> callback(result)
        }
    }

    private fun updateState(): State {
        currentState = when {
            repo.data.accessCode == null || repo.data.primaryCard == null || repo.data.backupCards.isEmpty() ->
                State.Preparing
            repo.data.attestSignature == null || repo.data.backupData.isEmpty() ->
                State.FinalizingPrimaryCard
            repo.data.finalizedBackupCardsCount < repo.data.backupCards.size ->
                State.FinalizingBackupCard(repo.data.finalizedBackupCardsCount + 1)
            else -> State.Finished
        }
        return currentState
    }

    private fun addBackupCard(backupCard: BackupCard) {
        val updatedList = repo.data.backupCards
            .filter { it.cardId != backupCard.cardId }
            .plus(backupCard)

        repo.data = repo.data.copy(backupCards = updatedList)
        updateState()

        fetchCertificate(backupCard.cardId, backupCard.cardPublicKey)
    }

    private fun readBackupCard(
        primaryCard: PrimaryCard,
        callback: CompletionCallback<Unit>,
    ) {
        sdk.startSessionWithRunnable(
            runnable = StartBackupCardLinkingTask(
                primaryCard,
                repo.data.backupCards.map { it.cardId }
            ),
            initialMessage = Message(header =
            stringsLocator.getString(StringsLocator.ID.backup_add_backup_card_message))
        ) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    addBackupCard(result.data)
                    callback(CompletionResult.Success(Unit))
                }
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }

        }
    }

    private fun handleFinalizePrimaryCard(callback: CompletionCallback<Card>) {
        try {
            if (handleErrors) {
                if (repo.data.accessCode == null && repo.data.passcode == null) {
                    throw TangemSdkError.AccessCodeOrPasscodeRequired()
                }
            }
            val accessCode =
                repo.data.accessCode ?: UserCodeType.AccessCode.defaultValue.calculateSha256()
            val passcode =
                repo.data.passcode ?: UserCodeType.Passcode.defaultValue.calculateSha256()
            val primaryCard =
                repo.data.primaryCard.guard { throw TangemSdkError.MissingPrimaryCard() }

            val linkableBackupCards = repo.data.backupCards.map { card ->
                val certificate = repo.data.certificates[card.cardId]
                    .guard { throw TangemSdkError.CertificateSignatureRequired() }
                card.makeLinkable(certificate)
            }

            if (handleErrors) {
                if (linkableBackupCards.isEmpty()) throw TangemSdkError.EmptyBackupCards()
                if (linkableBackupCards.size > MAX_BACKUP_CARDS_COUNT) throw TangemSdkError.TooMuchBackupCards()
            }

            val task = FinalizePrimaryCardTask(
                backupCards = linkableBackupCards,
                accessCode = accessCode,
                passcode = passcode,
                readBackupStartIndex = repo.data.backupData.size,
                attestSignature = repo.data.attestSignature,
                onLink = { repo.data = repo.data.copy(attestSignature = it) },
                onRead = { cardId, data ->
                    repo.data = repo.data.copy(
                        backupData = repo.data.backupData.plus(Pair(cardId, data)
                        ))
                }
            )
            val formattedCardId = CardIdFormatter(style = CardIdDisplayFormat.LastMasked(4))
                .getFormattedCardId(primaryCard.cardId)
            val message = Message(
                header = null,
                body = stringsLocator.getString(
                    StringsLocator.ID.backup_finalize_primary_card_message_format,
                    formattedCardId
                )
            )

            sdk.startSessionWithRunnable(
                task, primaryCard.cardId,
                initialMessage = message,
                callback
            )
        } catch (error: TangemSdkError) {
            callback(CompletionResult.Failure(error))
            return
        }
    }

    private fun handleWriteBackupCard(index: Int, callback: CompletionCallback<Card>) {
        try {
            if (handleErrors) {
                if (repo.data.accessCode == null && repo.data.passcode == null) {
                    throw TangemSdkError.AccessCodeOrPasscodeRequired()
                }
            }
            val accessCode =
                repo.data.accessCode ?: UserCodeType.AccessCode.defaultValue.calculateSha256()
            val passcode =
                repo.data.passcode ?: UserCodeType.Passcode.defaultValue.calculateSha256()

            val attestSignature =
                repo.data.attestSignature.guard { throw TangemSdkError.MissingPrimaryAttestSignature() }
            val primaryCard =
                repo.data.primaryCard.guard { throw TangemSdkError.MissingPrimaryCard() }
            val primaryCardCertificate =
                repo.data.certificates[primaryCard.cardId].guard { throw TangemSdkError.CertificateSignatureRequired() }

            val cardIndex = index - 1

            if (handleErrors) {
                if (repo.data.backupCards.size > MAX_BACKUP_CARDS_COUNT) {
                    throw TangemSdkError.TooMuchBackupCards()
                }
            }

            val backupCards = repo.data.backupCards
            val backupCard = backupCards.getOrNull(cardIndex).guard {
                throw TangemSdkError.NoBackupCardForIndex()
            }
            val backupData = repo.data.backupData[backupCard.cardId].guard {
                throw TangemSdkError.NoBackupDataForCard()
            }

            val task = FinalizeBackupCardTask(
                primaryCard = primaryCard.makeLinkable(primaryCardCertificate),
                backupCards = backupCards,
                backupData = backupData,
                attestSignature = attestSignature,
                accessCode = accessCode,
                passcode = passcode
            )

            val formattedCardId = CardIdFormatter(style = CardIdDisplayFormat.LastMasked(4))
                    .getFormattedCardId(backupCard.cardId)
            val message = Message(
                header = null,
                body = stringsLocator.getString(
                    StringsLocator.ID.backup_finalize_backup_card_message_format,
                    formattedCardId
                )
            )

            sdk.startSessionWithRunnable(
                task, backupCard.cardId,
                initialMessage = message
            ) { result ->
                when (result) {
                    is CompletionResult.Success -> {
                        repo.data = repo.data.copy(
                            finalizedBackupCardsCount = repo.data.finalizedBackupCardsCount + 1)
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
    }

    private fun fetchCertificate(
        cardId: String,
        cardPublicKey: ByteArray,
    ) {
        //TODO: get from our server
        val issuerPrivateKey = "11121314151617184771ED81F2BACF57479E4735EB1405083927372D40DA9E92"
            .hexToBytes()
        val signature = cardPublicKey.sign(issuerPrivateKey)
        val certificate = TlvBuilder().apply {
            append(TlvTag.CardPublicKey, cardPublicKey)
            append(TlvTag.IssuerDataSignature, signature)
        }.serialize()
        repo.data = repo.data.copy(
            certificates = repo.data.certificates.plus(Pair(cardId, certificate))
        )
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

@JsonClass(generateAdapter = true)
class PrimaryCard(
    val cardId: String,
    val cardPublicKey: ByteArray,
    val linkingKey: ByteArray,
    //For compatibility check with backup card
    val existingWalletsCount: Int,
    val isHDWalletAllowed: Boolean,
    val issuer: Card.Issuer,
    val walletCurves: List<EllipticCurve>
) : CommandResponse {
    fun makeLinkable(certificate: ByteArray): LinkablePrimaryCard {
        return LinkablePrimaryCard(
            cardId = cardId,
            cardPublicKey = cardPublicKey,
            linkingKey = linkingKey,
            certificate = certificate)
    }
}

@JsonClass(generateAdapter = true)
class LinkablePrimaryCard(
    val cardId: String,
    val cardPublicKey: ByteArray,
    val linkingKey: ByteArray,
    val certificate: ByteArray,
)

@JsonClass(generateAdapter = true)
class BackupCard(
    val cardId: String,
    val cardPublicKey: ByteArray,
    val linkingKey: ByteArray,
    val attestSignature: ByteArray,
) : CommandResponse {
    fun makeLinkable(certificate: ByteArray): LinkableBackupCard {
        return LinkableBackupCard(
            cardId = cardId,
            cardPublicKey = cardPublicKey,
            linkingKey = linkingKey,
            attestSignature = attestSignature,
            certificate = certificate)
    }
}

class EncryptedBackupData(val data: ByteArray, val salt: ByteArray)

class LinkableBackupCard(
    val cardId: String,
    val cardPublicKey: ByteArray,
    val linkingKey: ByteArray,
    val attestSignature: ByteArray,
    val certificate: ByteArray,
)

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
package com.tangem.operations.attestation

import com.tangem.common.CompletionResult
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.*
import com.tangem.common.extensions.guard
import com.tangem.common.json.MoshiJsonConverter
import com.tangem.common.map
import com.tangem.common.services.Result
import com.tangem.common.services.TrustedCardsRepo
import com.tangem.common.services.secure.SecureStorage
import com.tangem.common.services.toTangemSdkError
import com.tangem.operations.attestation.api.TangemApiService
import com.tangem.operations.attestation.service.OnlineAttestationService
import com.tangem.operations.attestation.service.OnlineAttestationServiceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AttestationTask(
    private val mode: Mode,
    secureStorage: SecureStorage,
) : CardSessionRunnable<Attestation> {

    /**
     * If `true`, AttestationTask will not pause NFC session after all card operations complete. Useful
     * for chaining  tasks after AttestationTask. False by default
     */
    var shouldKeepSessionOpened = false

    private val trustedCardsRepo = TrustedCardsRepo(secureStorage, MoshiJsonConverter.INSTANCE)
    private val onlineCardVerifier: OnlineCardVerifier = OnlineCardVerifier()

    private var currentAttestationStatus: Attestation = Attestation.empty

    private var onlineAttestationChannel = ConflatedBroadcastChannel<CompletionResult<Any>>()
    private var onlineAttestationSubscription: ReceiveChannel<CompletionResult<*>>? = null

    override fun run(session: CardSession, callback: CompletionCallback<Attestation>) {
        session.environment.card.guard {
            callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
            return
        }

        attestCard(session, callback)
    }

    private fun attestCard(session: CardSession, callback: CompletionCallback<Attestation>) {
        AttestCardKeyCommand().run(session) { result ->
            handleOfflineAttestationResult(result = result, session = session, callback = callback)
        }
    }

    private fun handleOfflineAttestationResult(
        result: CompletionResult<AttestCardKeyResponse>,
        session: CardSession,
        callback: CompletionCallback<Attestation>,
    ) {
        when (result) {
            is CompletionResult.Success -> handleSuccessOfflineAttestation(session = session, callback = callback)
            is CompletionResult.Failure -> handleFailureOfflineAttestation(error = result.error, callback = callback)
        }
    }

    /**
     * Handle the result of success offline attestation.
     * Terminate the attestation process if card was already attested or start online attestation process.
     *
     * @param session  session
     * @param callback callback
     */
    private fun handleSuccessOfflineAttestation(session: CardSession, callback: CompletionCallback<Attestation>) {
        // This card already attested with the current or more secured mode
        val attestation = trustedCardsRepo.attestation(session.environment.card!!.cardPublicKey)

        val isAlreadyAttested = attestation != null && attestation.mode.ordinal >= mode.ordinal
        if (isAlreadyAttested) {
            currentAttestationStatus = requireNotNull(attestation)
            complete(session, callback)
        } else {
            currentAttestationStatus = currentAttestationStatus.copy(
                cardKeyAttestation = Attestation.Status.VerifiedOffline,
            )

            continueAttestation(session, callback)
        }
    }

    /**
     * Handle the result of failure offline attestation. Terminate the attestation process.
     *
     * @param error    error
     * @param callback callback
     */
    private fun handleFailureOfflineAttestation(error: TangemError, callback: CompletionCallback<Attestation>) {
        if (error is TangemSdkError.CardVerificationFailed) {
            currentAttestationStatus = currentAttestationStatus.copy(
                cardKeyAttestation = Attestation.Status.Failed,
            )
        }

        callback(CompletionResult.Failure(error))
    }

    private fun continueAttestation(session: CardSession, callback: CompletionCallback<Attestation>) {
        when (mode) {
            Mode.Offline -> complete(session, callback)
            Mode.Normal -> {
                runOnlineAttestation(session.scope, session.environment.card!!, session.environment)
                waitForOnlineAndComplete(session, callback)
            }
            Mode.Full -> {
                runOnlineAttestation(session.scope, session.environment.card!!, session.environment)
                runWalletsAttestation(session, callback)
            }
        }
    }

    /**
     * Dev card will not pass online attestation. Or, if the card already failed offline attestation,
     * we can skip online part. So, we can send the error to the publisher immediately
     */
    private fun runOnlineAttestation(scope: CoroutineScope, card: Card, environment: SessionEnvironment) {
        if (environment.config.isNewOnlineAttestationEnabled) {
            val factory = OnlineAttestationServiceFactory(
                tangemApiService = TangemApiService(isProdEnvironment = environment.config.isTangemAttestationProdEnv),
                secureStorage = environment.secureStorage,
            )

            runNewOnlineAttestation(scope = scope, card = card, service = factory.create(card))
        } else {
            runOldOnlineAttestation(scope, card)
        }
    }

    private fun runOldOnlineAttestation(scope: CoroutineScope, card: Card) {
        scope.launch(Dispatchers.IO) {
            val isDevelopmentCard = card.firmwareVersion.type == FirmwareVersion.FirmwareType.Sdk
            val isAttestationFailed = currentAttestationStatus.cardKeyAttestation == Attestation.Status.Failed
            if (isDevelopmentCard || isAttestationFailed) {
                onlineAttestationChannel.send(CompletionResult.Failure(TangemSdkError.CardVerificationFailed()))
                return@launch
            }

            when (val result = onlineCardVerifier.getCardInfo(card.cardId, card.cardPublicKey)) {
                is Result.Success -> onlineAttestationChannel.send(CompletionResult.Success(result.data))
                is Result.Failure -> onlineAttestationChannel.send(CompletionResult.Failure(result.toTangemSdkError()))
            }
        }
    }

    private fun runNewOnlineAttestation(scope: CoroutineScope, card: Card, service: OnlineAttestationService) {
        scope.launch(Dispatchers.IO) {
            val isDevelopmentCard = card.firmwareVersion.type == FirmwareVersion.FirmwareType.Sdk

            if (isDevelopmentCard) {
                onlineAttestationChannel.send(CompletionResult.Failure(TangemSdkError.CardVerificationFailed()))
                return@launch
            }

            val result: CompletionResult<Any> = service.attestCard(
                cardId = card.cardId,
                cardPublicKey = card.cardPublicKey,
            )
                .map { /* cast to Any */ }

            onlineAttestationChannel.send(result)
        }
    }

    /**
     * Wait for online and handle the online attestation result
     *
     * @param session  session
     * @param callback callback
     */
    private fun waitForOnlineAndComplete(session: CardSession, callback: CompletionCallback<Attestation>) {
        if (onlineAttestationSubscription != null) return

        if (!shouldKeepSessionOpened) {
            session.pause()
        }

        session.scope.launch {
            onlineAttestationSubscription = onlineAttestationChannel.openSubscription()
            onlineAttestationSubscription?.consumeEach { result ->
                when (result) {
                    is CompletionResult.Success -> {
                        // We assume, that card verified, because we skip online attestation for dev cards and cards that failed keys attestation
                        currentAttestationStatus = currentAttestationStatus.copy(
                            cardKeyAttestation = Attestation.Status.Verified,
                        )
                        trustedCardsRepo.append(session.environment.card!!.cardPublicKey, currentAttestationStatus)
                        processAttestationReport(session, callback)
                    }
                    is CompletionResult.Failure -> {
                        // We interest only in cardVerificationFailed error, ignore network errors
                        if (result.error is TangemSdkError.CardVerificationFailed) {
                            currentAttestationStatus = currentAttestationStatus.copy(
                                cardKeyAttestation = Attestation.Status.Failed,
                            )
                        }
                        processAttestationReport(session, callback)
                    }
                }
            }
        }
    }

    private fun processAttestationReport(session: CardSession, callback: CompletionCallback<Attestation>) {
        when (currentAttestationStatus.status) {
            Attestation.Status.Failed,
            Attestation.Status.Skipped,
            -> {
                val isDevelopmentCard = session.environment.card!!.firmwareVersion.type ==
                    FirmwareVersion.FirmwareType.Sdk

                // Possible production sample or development card
                if (isDevelopmentCard) {
                    session.viewDelegate.attestationDidFail(
                        isDevCard = isDevelopmentCard,
                        positive = { complete(session, callback) },
                        negative = { callback(CompletionResult.Failure(TangemSdkError.UserCancelled())) },
                    )
                } else {
                    callback(CompletionResult.Failure(TangemSdkError.CardVerificationFailed()))
                }
            }
            Attestation.Status.Verified -> complete(session, callback)
            Attestation.Status.VerifiedOffline -> {
                if (session.environment.config.attestationMode == Mode.Offline) {
                    complete(session, callback)
                    return
                }

                session.viewDelegate.attestationCompletedOffline(
                    positive = { complete(session, callback) },
                    negative = { callback(CompletionResult.Failure(TangemSdkError.UserCancelled())) },
                    retry = {
                        retryOnline(session) { result ->
                            when (result) {
                                is CompletionResult.Success -> processAttestationReport(session, callback)
                                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                            }
                        }
                    },
                )
            }
            Attestation.Status.Warning -> {
                session.viewDelegate.attestationCompletedWithWarnings {
                    complete(session, callback)
                }
            }
        }
    }

    private fun retryOnline(session: CardSession, callback: CompletionCallback<Attestation>) {
        onlineAttestationSubscription = null
        onlineAttestationChannel.cancel()
        onlineAttestationChannel = ConflatedBroadcastChannel()

        val card = session.environment.card.guard {
            callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
            return
        }

        runOnlineAttestation(scope = session.scope, card = card, environment = session.environment)
        waitForOnlineAndComplete(session, callback)
    }

    private fun runWalletsAttestation(session: CardSession, callback: CompletionCallback<Attestation>) {
        attestWallets(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    // Wallets attestation completed. Update status and continue attestation
                    val hasWarnings = result.data
                    val status = if (hasWarnings) Attestation.Status.Warning else Attestation.Status.Verified
                    currentAttestationStatus = currentAttestationStatus.copy(walletKeysAttestation = status)
                    runExtraAttestation(session, callback)
                }
                is CompletionResult.Failure -> {
                    // Wallets attestation failed. Update status and continue attestation
                    if (result.error is TangemSdkError.CardVerificationFailed) {
                        currentAttestationStatus = currentAttestationStatus.copy(
                            walletKeysAttestation = Attestation.Status.Failed,
                        )
                        callback(CompletionResult.Failure(result.error))
                    } else {
                        callback(CompletionResult.Failure(result.error))
                    }
                }
            }
        }
    }

    private fun attestWallets(session: CardSession, callback: CompletionCallback<Boolean>) {
        session.scope.launch {
            val card = session.environment.card!!
            val walletsKeys = card.wallets.map { it.publicKey }
            val attestationCommands = walletsKeys.map { AttestWalletKeyTask(it) }

            // check for hacking attempts with signs
            var hasWarnings = card.wallets.mapNotNull { it.totalSignedHashes }.any { it > MAX_COUNTER }
            var shouldReturn = false
            var flowIsCompleted = false

            if (attestationCommands.isEmpty()) {
                callback(CompletionResult.Success(hasWarnings))
                return@launch
            }

            flow {
                attestationCommands.forEach { emit(it) }
            }.onCompletion {
                flowIsCompleted = true
            }.collect {
                if (shouldReturn) return@collect

                it.run(session) { result ->
                    when (result) {
                        is CompletionResult.Success -> {
                            // check for hacking attempts with attestWallet
                            if (result.data.counter != null && result.data.counter > MAX_COUNTER) {
                                hasWarnings = true
                            }
                            if (flowIsCompleted) callback(CompletionResult.Success(hasWarnings))
                        }
                        is CompletionResult.Failure -> {
                            shouldReturn = true
                            callback(CompletionResult.Failure(result.error))
                        }
                    }
                }
            }
        }
    }

    private fun runExtraAttestation(session: CardSession, callback: CompletionCallback<Attestation>) {
        // TODO: ATTEST_CARD_FIRMWARE, ATTEST_CARD_UNIQUENESS
        waitForOnlineAndComplete(session, callback)
    }

    private fun complete(session: CardSession, callback: CompletionCallback<Attestation>) {
        session.environment.card = session.environment.card?.copy(attestation = currentAttestationStatus)
        callback(CompletionResult.Success(currentAttestationStatus))

        onlineAttestationChannel.cancel()
        onlineAttestationSubscription = null
    }

    enum class Mode {
        Offline,
        Normal,
        Full,
    }

    companion object {
        /**
         * Attest wallet count or sign command count greater this value is looks suspicious.
         */
        private const val MAX_COUNTER = 100000
    }
}
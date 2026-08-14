package com.tangem.common.services.secure

import com.tangem.Log
import com.tangem.common.UserCode
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.doOnFailure
import com.tangem.common.doOnSuccess
import com.tangem.common.v8.CardAccessTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache for the access data set on the cards at the finalize step of the backup process
 * while biometrics is disabled ([com.tangem.common.core.UserCodeRequestPolicy] is not
 * `AlwaysWithBiometrics` or biometric authentication is unavailable), i.e. while

 *

 * per session. Once the user enables biometrics, the cached data is persisted to the repositories
 * via [flushTo].
 */
class RuntimeAccessDataCache {

    private class CachedUserCode(
        val userCode: UserCode,
        val firmwareVersion: FirmwareVersion,
    )

    private val userCodes = ConcurrentHashMap<String, CachedUserCode>()
    private val cardAccessTokens = ConcurrentHashMap<String, CardAccessTokens>()

    val isEmpty: Boolean
        get() = userCodes.isEmpty() && cardAccessTokens.isEmpty()

    /**
     * Remembers a user code verified by the card. A default code means the user removed the code
     * from the card, so the cached entry is dropped. Codes for FW v8+ cards are ignored, they use
     * access tokens instead (see [putCardAccessTokens]).
     */
    fun putUserCode(cardId: String, userCode: UserCode, firmwareVersion: FirmwareVersion) {
        if (firmwareVersion >= FirmwareVersion.v8) return
        if (userCode.value == null) return

        if (userCode.isDefault()) {
            userCodes.remove(cardId)
        } else {
            userCodes[cardId] = CachedUserCode(userCode, firmwareVersion)
        }
    }

    fun putCardAccessTokens(cardId: String, tokens: CardAccessTokens) {
        cardAccessTokens[cardId] = tokens
    }

    fun removeUserCodes(cardsIds: Collection<String>) {
        cardsIds.forEach { userCodes.remove(it) }
    }

    fun removeCardAccessTokens(cardsIds: Collection<String>) {
        cardsIds.forEach { cardAccessTokens.remove(it) }
    }

    fun clear() {
        userCodes.clear()
        cardAccessTokens.clear()
    }

    /**
     * Persists the cached data to the given repositories. Entries saved successfully are removed
     * from the cache; failed ones are kept to be retried on the next flush. Passing a `null`
     * repository keeps the corresponding entries cached.
     */
    suspend fun flushTo(
        accessCodeRepository: AccessCodeRepository?,
        cardAccessTokensRepository: CardAccessTokensRepository?,
    ) = withContext(Dispatchers.IO) {
        if (isEmpty) return@withContext

        if (accessCodeRepository != null) {
            for ((cardId, cached) in userCodes) {
                accessCodeRepository.save(cardId, cached.userCode, cached.firmwareVersion)
                    .doOnSuccess { userCodes.remove(cardId) }
                    .doOnFailure { error ->
                        Log.error { "Failed to flush cached user code for card $cardId: $error" }
                    }
            }
        }

        if (cardAccessTokensRepository != null) {
            for ((cardId, tokens) in cardAccessTokens) {
                try {
                    cardAccessTokensRepository.save(tokens, cardId)
                    if (cardAccessTokensRepository.contains(cardId)) {
                        cardAccessTokens.remove(cardId)
                    }
                } catch (e: Exception) {
                    Log.error { "Failed to flush cached access tokens for card $cardId: $e" }
                }
            }
        }
    }
}
package com.tangem.common.services.secure

import com.tangem.common.CompletionResult
import com.tangem.common.catching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unlocks several secure repositories at once.
 *
 * Both [AccessCodeRepository] and [CardAccessTokensRepository] persist their data in the same
 * underlying [com.tangem.common.authentication.storage.AuthenticatedStorage] (they share the same
 * keystore manager and secure storage). This allows fetching and decrypting the data of both
 * repositories with a single [com.tangem.common.authentication.storage.AuthenticatedStorage.get]
 * call, so the user is prompted for authentication (biometrics) only once.
 */
class AccessDataUnlocker(
    val accessCodeRepository: AccessCodeRepository?,
    val cardAccessTokensRepository: CardAccessTokensRepository?,
) {

    suspend fun unlockAll(): CompletionResult<Unit> = withContext(Dispatchers.IO) {
        catching {
            // Both repositories use an equivalent AuthenticatedStorage, so any of them can be
            // used to fetch the data for all the keys with a single authentication.
            val storage = accessCodeRepository?.authenticatedStorage
                ?: cardAccessTokensRepository?.authenticatedStorage
                ?: return@catching

            val keys = buildList {
                accessCodeRepository?.let { addAll(it.getUnlockKeys()) }
                cardAccessTokensRepository?.let { addAll(it.getUnlockKeys()) }
            }

            if (keys.isEmpty()) return@catching

            val decodedData = storage.get(keys)
            try {
                accessCodeRepository?.proceedUnlock(decodedData)
                cardAccessTokensRepository?.proceedUnlock(decodedData)
            } finally {
                decodedData.values.forEach { it.fill(0) }
            }
        }
    }
}
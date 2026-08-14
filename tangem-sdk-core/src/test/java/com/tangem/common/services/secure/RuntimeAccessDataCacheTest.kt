package com.tangem.common.services.secure

import com.google.common.truth.Truth.assertThat
import com.tangem.common.CompletionResult
import com.tangem.common.UserCode
import com.tangem.common.UserCodeType
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.TangemSdkError
import com.tangem.common.v8.CardAccessTokens
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RuntimeAccessDataCacheTest {

    private val cache = RuntimeAccessDataCache()

    private val cardId = "CB42000000001234"
    private val userCode = UserCode(UserCodeType.AccessCode, "123456")
    private val defaultUserCode = UserCode(UserCodeType.AccessCode, UserCode.DefaultAccessCode)
    private val fwV4 = FirmwareVersion(major = 4, minor = 12)
    private val fwV8 = FirmwareVersion.v8
    private val tokens = CardAccessTokens(
        accessToken = byteArrayOf(1, 2, 3),
        identifyToken = byteArrayOf(4, 5, 6),
    )

    @Test
    fun `putUserCode caches non-default code`() {
        cache.putUserCode(cardId, userCode, fwV4)

        assertThat(cache.isEmpty).isFalse()
    }

    @Test
    fun `putUserCode with default code removes cached entry`() {
        cache.putUserCode(cardId, userCode, fwV4)

        cache.putUserCode(cardId, defaultUserCode, fwV4)

        assertThat(cache.isEmpty).isTrue()
    }

    @Test
    fun `putUserCode ignores v8 firmware`() {
        cache.putUserCode(cardId, userCode, fwV8)

        assertThat(cache.isEmpty).isTrue()
    }

    @Test
    fun `putUserCode ignores null code value`() {
        cache.putUserCode(cardId, UserCode(UserCodeType.AccessCode, value = null), fwV4)

        assertThat(cache.isEmpty).isTrue()
    }

    @Test
    fun `flushTo persists user code and clears cache on success`() = runTest {
        val repository = mockk<AccessCodeRepository>()
        coEvery { repository.save(cardId, userCode, fwV4) } returns CompletionResult.Success(Unit)
        cache.putUserCode(cardId, userCode, fwV4)

        cache.flushTo(accessCodeRepository = repository, cardAccessTokensRepository = null)

        coVerify(exactly = 1) { repository.save(cardId, userCode, fwV4) }
        assertThat(cache.isEmpty).isTrue()
    }

    @Test
    fun `flushTo keeps user code on failure and retries on the next flush`() = runTest {
        val repository = mockk<AccessCodeRepository>()
        coEvery {
            repository.save(cardId, userCode, fwV4)
        } returns CompletionResult.Failure(TangemSdkError.ExceptionError(Exception("storage error"))) andThen
            CompletionResult.Success(Unit)
        cache.putUserCode(cardId, userCode, fwV4)

        cache.flushTo(accessCodeRepository = repository, cardAccessTokensRepository = null)
        assertThat(cache.isEmpty).isFalse()

        cache.flushTo(accessCodeRepository = repository, cardAccessTokensRepository = null)
        assertThat(cache.isEmpty).isTrue()

        coVerify(exactly = 2) { repository.save(cardId, userCode, fwV4) }
    }

    @Test
    fun `flushTo with null repositories keeps cached data`() = runTest {
        cache.putUserCode(cardId, userCode, fwV4)
        cache.putCardAccessTokens(cardId, tokens)

        cache.flushTo(accessCodeRepository = null, cardAccessTokensRepository = null)

        assertThat(cache.isEmpty).isFalse()
    }

    @Test
    fun `flushTo persists tokens and clears cache when repository confirms saving`() = runTest {
        val repository = mockk<CardAccessTokensRepository>()
        coEvery { repository.save(tokens, cardId) } just Runs
        every { repository.contains(cardId) } returns true
        cache.putCardAccessTokens(cardId, tokens)

        cache.flushTo(accessCodeRepository = null, cardAccessTokensRepository = repository)

        coVerify(exactly = 1) { repository.save(tokens, cardId) }
        assertThat(cache.isEmpty).isTrue()
    }

    @Test
    fun `flushTo keeps tokens when repository did not save them`() = runTest {
        val repository = mockk<CardAccessTokensRepository>()
        coEvery { repository.save(tokens, cardId) } just Runs
        every { repository.contains(cardId) } returns false
        cache.putCardAccessTokens(cardId, tokens)

        cache.flushTo(accessCodeRepository = null, cardAccessTokensRepository = repository)

        assertThat(cache.isEmpty).isFalse()
    }

    @Test
    fun `removeCardAccessTokens removes entry`() {
        cache.putCardAccessTokens(cardId, tokens)

        cache.removeCardAccessTokens(setOf(cardId))

        assertThat(cache.isEmpty).isTrue()
    }

    @Test
    fun `removeUserCodes removes entry`() {
        cache.putUserCode(cardId, userCode, fwV4)

        cache.removeUserCodes(setOf(cardId))

        assertThat(cache.isEmpty).isTrue()
    }

    @Test
    fun `clear removes all entries`() {
        cache.putUserCode(cardId, userCode, fwV4)
        cache.putCardAccessTokens(cardId, tokens)

        cache.clear()

        assertThat(cache.isEmpty).isTrue()
    }
}
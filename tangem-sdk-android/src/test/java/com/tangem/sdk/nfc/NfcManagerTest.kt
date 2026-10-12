package com.tangem.sdk.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.os.DeadObjectException
import androidx.lifecycle.LifecycleOwner
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [NfcManager] reader-mode error handling.
 *
 * Every [NfcAdapter] method is a binder call into the system NFC service. When that service process dies
 * the platform re-throws the binder failure as an unchecked `RuntimeException(DeadObjectException)` on the
 * caller's thread — the main one here — which used to take the host app down (CRASHAND-137, CRASHAND-138).
 * These tests pin both halves of the fix: the throwable must not escape, and the failure must stay
 * visible through [NfcManager.readerModeFailed] so the session delegate can warn the user.
 */
internal class NfcManagerTest {

    private val adapter: NfcAdapter = mockk()
    private val activity: Activity = mockk()
    private val owner: LifecycleOwner = mockk()

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `dead NFC service on enable does not escape and is reported`() {
        val nfcManager = nfcManager()
        every { adapter.enableReaderMode(any(), any(), any(), any()) } throws deadNfcService()

        nfcManager.onStart(owner)

        assertTrue(nfcManager.readerModeFailed)
    }

    @Test
    fun `activity not resumed on enable does not escape and is reported`() {
        val nfcManager = nfcManager()
        every {
            adapter.enableReaderMode(any(), any(), any(), any())
        } throws IllegalStateException("Activity is not resumed")

        nfcManager.onStart(owner)

        assertTrue(nfcManager.readerModeFailed)
    }

    @Test
    fun `successful enable leaves the failure flag clear`() {
        val nfcManager = nfcManager()

        nfcManager.onStart(owner)

        assertFalse(nfcManager.readerModeFailed)
    }

    @Test
    fun `enable recovering after a failure clears the failure flag`() {
        val nfcManager = nfcManager()
        every { adapter.enableReaderMode(any(), any(), any(), any()) } throws deadNfcService()
        nfcManager.onStart(owner)
        assertTrue(nfcManager.readerModeFailed)

        justRun { adapter.enableReaderMode(any(), any(), any(), any()) }
        nfcManager.onStart(owner)

        assertFalse(nfcManager.readerModeFailed)
    }

    @Test
    fun `dead NFC service on disable does not escape`() {
        val nfcManager = nfcManager()
        nfcManager.onStart(owner)
        every { adapter.disableReaderMode(any()) } throws deadNfcService()

        nfcManager.onStop(owner)
    }

    @Test
    fun `dead NFC service on isEnabled reports NFC as unavailable`() {
        val nfcManager = nfcManager()
        every { adapter.isEnabled } throws deadNfcService()

        assertFalse(nfcManager.isNfcEnabled)
    }

    private fun deadNfcService() = RuntimeException(DeadObjectException())

    private fun nfcManager(): NfcManager {
        every { activity.isDestroyed } returns false
        every { adapter.isEnabled } returns true
        justRun { adapter.enableReaderMode(any(), any(), any(), any()) }
        justRun { adapter.disableReaderMode(any()) }

        mockkStatic(NfcAdapter::class)
        every { NfcAdapter.getDefaultAdapter(any()) } returns adapter

        return NfcManager().apply { setCurrentActivity(activity) }
    }
}
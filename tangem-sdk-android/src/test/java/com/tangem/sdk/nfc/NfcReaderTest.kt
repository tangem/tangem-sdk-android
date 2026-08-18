package com.tangem.sdk.nfc

import android.nfc.tech.IsoDep
import com.tangem.common.CompletionResult
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.StatusWord
import com.tangem.common.core.TagType
import com.tangem.common.core.TangemSdkError
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [NfcReader.transceiveApdu] error handling.
 *
 * Every failure of `IsoDep.transceive` must resume the suspended caller — a swallowed callback
 * hangs [com.tangem.common.core.CardSession.send] forever ([REDACTED_TASK_KEY]: infinite loader after
 * SecurityException "Tag is out of date" on re-scan following access code entry).
 */
internal class NfcReaderTest {

    private val apdu = CommandApdu(ins = 0, tlvs = byteArrayOf(), p1 = 0, p2 = 0)

    @Test
    fun `stale tag SecurityException resolves to TagLost`() {
        val reader = readerWithTransceive {
            throw SecurityException("Permission Denial: Tag ( ID: 64 3D 1B CC ) is out of date")
        }

        val result = runBlocking { reader.transceiveApdu(apdu) }

        assertTrue((result as CompletionResult.Failure).error is TangemSdkError.TagLost)
    }

    @Test
    fun `transceive IOException resolves to TagLost`() {
        val reader = readerWithTransceive {
            throw IOException("Transceive failed")
        }

        val result = runBlocking { reader.transceiveApdu(apdu) }

        assertTrue((result as CompletionResult.Failure).error is TangemSdkError.TagLost)
    }

    @Test
    fun `extended length error resolves to ExtendedLengthNotSupported`() {
        val reader = readerWithTransceive {
            throw IOException("Transceive length exceeds supported maximum")
        }

        val result = runBlocking { reader.transceiveApdu(apdu) }

        assertTrue((result as CompletionResult.Failure).error is TangemSdkError.ExtendedLengthNotSupported)
    }

    @Test
    fun `unexpected exception resolves to ExceptionError`() {
        val cause = IllegalStateException("something unexpected")
        val reader = readerWithTransceive { throw cause }

        val result = runBlocking { reader.transceiveApdu(apdu) }

        val error = (result as CompletionResult.Failure).error
        assertTrue(error is TangemSdkError.ExceptionError)
        assertEquals(cause, (error as TangemSdkError.ExceptionError).cause)
    }

    @Test
    fun `empty response resolves to TagLost`() {
        val reader = readerWithTransceive { byteArrayOf() }

        val result = runBlocking { reader.transceiveApdu(apdu) }

        assertTrue((result as CompletionResult.Failure).error is TangemSdkError.TagLost)
    }

    @Test
    fun `valid response resolves to Success`() {
        val reader = readerWithTransceive { byteArrayOf(0x90.toByte(), 0x00) }

        val result = runBlocking { reader.transceiveApdu(apdu) }

        assertEquals(StatusWord.ProcessCompleted, (result as CompletionResult.Success).data.statusWord)
    }

    private fun readerWithTransceive(onTransceive: (ByteArray) -> ByteArray): NfcReader {
        val isoDep = mockk<IsoDep> {
            every { isExtendedLengthApduSupported } returns true
            every { isConnected } returns true
            every { transceive(any()) } answers { onTransceive(firstArg()) }
        }
        return NfcReader().apply { setNfcTag(NfcTag(TagType.Nfc, isoDep)) }
    }

    // nfcTag is private and only assigned from Android tag discovery callbacks
    private fun NfcReader.setNfcTag(tag: NfcTag) {
        NfcReader::class.java.getDeclaredField("nfcTag")
            .apply { isAccessible = true }
            .set(this, tag)
    }
}
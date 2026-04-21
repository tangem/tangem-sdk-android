package com.tangem.crypto.bip39

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EntropyLengthTest {

    // region count (bit length)

    @Test
    fun bits128Count() {
        assertThat(EntropyLength.Bits128Length.count).isEqualTo(128)
    }

    @Test
    fun bits160Count() {
        assertThat(EntropyLength.Bits160Length.count).isEqualTo(160)
    }

    @Test
    fun bits192Count() {
        assertThat(EntropyLength.Bits192Length.count).isEqualTo(192)
    }

    @Test
    fun bits224Count() {
        assertThat(EntropyLength.Bits224Length.count).isEqualTo(224)
    }

    @Test
    fun bits256Count() {
        assertThat(EntropyLength.Bits256Length.count).isEqualTo(256)
    }

    // endregion

    // region wordCount

    @Test
    fun bits128WordCount() {
        assertThat(EntropyLength.Bits128Length.wordCount()).isEqualTo(12)
    }

    @Test
    fun bits160WordCount() {
        assertThat(EntropyLength.Bits160Length.wordCount()).isEqualTo(15)
    }

    @Test
    fun bits192WordCount() {
        assertThat(EntropyLength.Bits192Length.wordCount()).isEqualTo(18)
    }

    @Test
    fun bits224WordCount() {
        assertThat(EntropyLength.Bits224Length.wordCount()).isEqualTo(21)
    }

    @Test
    fun bits256WordCount() {
        assertThat(EntropyLength.Bits256Length.wordCount()).isEqualTo(24)
    }

    // endregion

    // region checksumBitsCount

    @Test
    fun bits128ChecksumBits() {
        assertThat(EntropyLength.Bits128Length.checksumBitsCount()).isEqualTo(4)
    }

    @Test
    fun bits160ChecksumBits() {
        assertThat(EntropyLength.Bits160Length.checksumBitsCount()).isEqualTo(5)
    }

    @Test
    fun bits192ChecksumBits() {
        assertThat(EntropyLength.Bits192Length.checksumBitsCount()).isEqualTo(6)
    }

    @Test
    fun bits224ChecksumBits() {
        assertThat(EntropyLength.Bits224Length.checksumBitsCount()).isEqualTo(7)
    }

    @Test
    fun bits256ChecksumBits() {
        assertThat(EntropyLength.Bits256Length.checksumBitsCount()).isEqualTo(8)
    }

    // endregion

    // region toBytes

    @Test
    fun bits128ToBytes() {
        assertThat(EntropyLength.Bits128Length.toBytes()).isEqualTo(16)
    }

    @Test
    fun bits160ToBytes() {
        assertThat(EntropyLength.Bits160Length.toBytes()).isEqualTo(20)
    }

    @Test
    fun bits192ToBytes() {
        assertThat(EntropyLength.Bits192Length.toBytes()).isEqualTo(24)
    }

    @Test
    fun bits224ToBytes() {
        assertThat(EntropyLength.Bits224Length.toBytes()).isEqualTo(28)
    }

    @Test
    fun bits256ToBytes() {
        assertThat(EntropyLength.Bits256Length.toBytes()).isEqualTo(32)
    }

    // endregion

    // region create from word count

    @Test
    fun createFromWordCount12() {
        assertThat(EntropyLength.create(12)).isEqualTo(EntropyLength.Bits128Length)
    }

    @Test
    fun createFromWordCount15() {
        assertThat(EntropyLength.create(15)).isEqualTo(EntropyLength.Bits160Length)
    }

    @Test
    fun createFromWordCount18() {
        assertThat(EntropyLength.create(18)).isEqualTo(EntropyLength.Bits192Length)
    }

    @Test
    fun createFromWordCount21() {
        assertThat(EntropyLength.create(21)).isEqualTo(EntropyLength.Bits224Length)
    }

    @Test
    fun createFromWordCount24() {
        assertThat(EntropyLength.create(24)).isEqualTo(EntropyLength.Bits256Length)
    }

    @Test(expected = IllegalStateException::class)
    fun createFromInvalidWordCountThrows() {
        EntropyLength.create(10)
    }

    @Test(expected = IllegalStateException::class)
    fun createFromZeroWordCountThrows() {
        EntropyLength.create(0)
    }

    // endregion

    // region create from entropy data

    @Test
    fun createFromEntropyData16Bytes() {
        assertThat(EntropyLength.create(ByteArray(16))).isEqualTo(EntropyLength.Bits128Length)
    }

    @Test
    fun createFromEntropyData20Bytes() {
        assertThat(EntropyLength.create(ByteArray(20))).isEqualTo(EntropyLength.Bits160Length)
    }

    @Test
    fun createFromEntropyData24Bytes() {
        assertThat(EntropyLength.create(ByteArray(24))).isEqualTo(EntropyLength.Bits192Length)
    }

    @Test
    fun createFromEntropyData28Bytes() {
        assertThat(EntropyLength.create(ByteArray(28))).isEqualTo(EntropyLength.Bits224Length)
    }

    @Test
    fun createFromEntropyData32Bytes() {
        assertThat(EntropyLength.create(ByteArray(32))).isEqualTo(EntropyLength.Bits256Length)
    }

    @Test(expected = IllegalStateException::class)
    fun createFromInvalidEntropyDataThrows() {
        EntropyLength.create(ByteArray(10))
    }

    @Test(expected = IllegalStateException::class)
    fun createFromEmptyEntropyDataThrows() {
        EntropyLength.create(ByteArray(0))
    }

    // endregion

    // region Relationships between properties

    @Test
    fun wordCountMatchesFormula() {
        // BIP39: wordCount = (entropyBits + checksumBits) / 11
        val allLengths = listOf(
            EntropyLength.Bits128Length,
            EntropyLength.Bits160Length,
            EntropyLength.Bits192Length,
            EntropyLength.Bits224Length,
            EntropyLength.Bits256Length,
        )
        allLengths.forEach { length ->
            val expected = (length.count + length.checksumBitsCount()) / 11
            assertThat(length.wordCount()).isEqualTo(expected)
        }
    }

    @Test
    fun toBytesMatchesCountDividedBy8() {
        val allLengths = listOf(
            EntropyLength.Bits128Length,
            EntropyLength.Bits160Length,
            EntropyLength.Bits192Length,
            EntropyLength.Bits224Length,
            EntropyLength.Bits256Length,
        )
        allLengths.forEach { length ->
            assertThat(length.toBytes()).isEqualTo(length.count / 8)
        }
    }

    @Test
    fun createRoundtripViaWordCount() {
        val allLengths = listOf(
            EntropyLength.Bits128Length,
            EntropyLength.Bits160Length,
            EntropyLength.Bits192Length,
            EntropyLength.Bits224Length,
            EntropyLength.Bits256Length,
        )
        allLengths.forEach { length ->
            assertThat(EntropyLength.create(length.wordCount())).isEqualTo(length)
        }
    }

    @Test
    fun createRoundtripViaEntropyData() {
        val allLengths = listOf(
            EntropyLength.Bits128Length,
            EntropyLength.Bits160Length,
            EntropyLength.Bits192Length,
            EntropyLength.Bits224Length,
            EntropyLength.Bits256Length,
        )
        allLengths.forEach { length ->
            assertThat(EntropyLength.create(ByteArray(length.toBytes()))).isEqualTo(length)
        }
    }

    // endregion
}
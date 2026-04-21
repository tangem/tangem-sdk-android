package com.tangem.operations.personalization.config

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CardIdBuilderTest {

    // region createCardId with series, startNumber

    @Test
    fun simpleCardIdWith2CharSeries() {
        val cardId = CardIdBuilder.createCardId(series = "CB", startNumber = 1)
        assertThat(cardId).isNotNull()
        assertThat(cardId).startsWith("CB")
        assertThat(cardId).hasLength(16)
    }

    @Test
    fun simpleCardIdWith4CharSeries() {
        val cardId = CardIdBuilder.createCardId(series = "CB79", startNumber = 1)
        assertThat(cardId).isNotNull()
        assertThat(cardId).startsWith("CB79")
        assertThat(cardId).hasLength(16)
    }

    @Test
    fun simpleCardIdPadsNumberWithZeros() {
        val cardId = CardIdBuilder.createCardId(series = "CB79", startNumber = 42)
        assertThat(cardId).isNotNull()
        // 4-char series → 11-digit padded number + 1 luhn = 16 total
        // "CB79" + "00000000042" + luhn
        assertThat(cardId!!.substring(4, 15)).isEqualTo("00000000042")
    }

    @Test
    fun simpleCardIdWithZeroStartNumber() {
        val cardId = CardIdBuilder.createCardId(series = "CB79", startNumber = 0)
        assertThat(cardId).isNotNull()
        assertThat(cardId!!.substring(4, 15)).isEqualTo("00000000000")
    }

    @Test
    fun simpleCardIdWith2CharSeriesPads13Digits() {
        val cardId = CardIdBuilder.createCardId(series = "CB", startNumber = 5)
        assertThat(cardId).isNotNull()
        // 2-char series → 13-digit padded number + 1 luhn = 16 total
        assertThat(cardId!!.substring(2, 15)).isEqualTo("0000000000005")
    }

    @Test
    fun cardNumberAddsToStartNumber() {
        val cardId1 = CardIdBuilder.createCardId(series = "CB79", startNumber = 100, cardNumber = 0)
        val cardId2 = CardIdBuilder.createCardId(series = "CB79", startNumber = 100, cardNumber = 5)
        assertThat(cardId1).isNotNull()
        assertThat(cardId2).isNotNull()
        // startNumber + cardNumber: 100 vs 105
        assertThat(cardId1!!.substring(4, 15)).isEqualTo("00000000100")
        assertThat(cardId2!!.substring(4, 15)).isEqualTo("00000000105")
    }

    // endregion

    // region Luhn checksum

    @Test
    fun cardIdLastCharIsLuhnChecksum() {
        val cardId = CardIdBuilder.createCardId(series = "CB79", startNumber = 18201)
        assertThat(cardId).isNotNull()
        assertThat(verifyLuhn(cardId!!)).isTrue()
    }

    @Test
    fun differentCardIdsAllHaveValidLuhn() {
        listOf(0L, 1L, 42L, 999L, 12345L, 99999999L).forEach { number ->
            val cardId = CardIdBuilder.createCardId(series = "AB12", startNumber = number)
            assertThat(cardId).isNotNull()
            assertThat(verifyLuhn(cardId!!)).isTrue()
        }
    }

    // endregion

    // region Null / invalid inputs

    @Test
    fun nullSeriesReturnsNull() {
        val cardId = CardIdBuilder.createCardId(series = null, startNumber = 1)
        assertThat(cardId).isNull()
    }

    @Test
    fun negativeStartNumberReturnsNull() {
        val cardId = CardIdBuilder.createCardId(series = "CB79", startNumber = -1)
        assertThat(cardId).isNull()
    }

    @Test
    fun negativeCardNumberReturnsNull() {
        val cardId = CardIdBuilder.createCardId(series = "CB79", startNumber = 0, cardNumber = -1)
        assertThat(cardId).isNull()
    }

    @Test
    fun invalidSeriesLength1ReturnsNull() {
        val cardId = CardIdBuilder.createCardId(series = "C", startNumber = 1)
        assertThat(cardId).isNull()
    }

    @Test
    fun invalidSeriesLength3ReturnsNull() {
        val cardId = CardIdBuilder.createCardId(series = "CB7", startNumber = 1)
        assertThat(cardId).isNull()
    }

    @Test
    fun invalidSeriesLength5ReturnsNull() {
        val cardId = CardIdBuilder.createCardId(series = "CB790", startNumber = 1)
        assertThat(cardId).isNull()
    }

    @Test
    fun invalidSeriesCharsReturnsNull() {
        val cardId = CardIdBuilder.createCardId(series = "ZZ", startNumber = 1)
        assertThat(cardId).isNull()
    }

    @Test
    fun seriesWithLowercaseReturnsNull() {
        val cardId = CardIdBuilder.createCardId(series = "cb", startNumber = 1)
        assertThat(cardId).isNull()
    }

    @Test
    fun emptySeriesReturnsNull() {
        val cardId = CardIdBuilder.createCardId(series = "", startNumber = 1)
        assertThat(cardId).isNull()
    }

    // endregion

    // region Valid series characters

    @Test
    fun seriesWithHexLetters() {
        val cardId = CardIdBuilder.createCardId(series = "AB", startNumber = 1)
        assertThat(cardId).isNotNull()
    }

    @Test
    fun seriesWithDigits() {
        val cardId = CardIdBuilder.createCardId(series = "12", startNumber = 1)
        assertThat(cardId).isNotNull()
    }

    @Test
    fun seriesWithMixedHexDigits() {
        val cardId = CardIdBuilder.createCardId(series = "A1B2", startNumber = 1)
        assertThat(cardId).isNotNull()
    }

    // endregion

    // region Formatted card ID (numberFormat)

    @Test
    fun formattedCardIdWithAllNs() {
        // 4-char series → tail length = 11
        val cardId = CardIdBuilder.createCardId(
            series = "CB79",
            startNumber = 42,
            numberFormat = "NNNNNNNNNNN",
        )
        assertThat(cardId).isNotNull()
        assertThat(cardId).hasLength(16)
        assertThat(cardId).startsWith("CB79")
        // N positions should contain the number digits
        assertThat(cardId!!.substring(4, 15)).isEqualTo("00000000042")
    }

    @Test
    fun formattedCardIdWithFixedHexChars() {
        val cardId = CardIdBuilder.createCardId(
            series = "CB79",
            startNumber = 1,
            numberFormat = "AANNNNNNNN0",
        )
        assertThat(cardId).isNotNull()
        assertThat(cardId).hasLength(16)
        // Fixed chars 'A', 'A' at positions 0,1 and '0' at position 10
        assertThat(cardId!![4]).isEqualTo('A')
        assertThat(cardId[5]).isEqualTo('A')
        assertThat(cardId[14]).isEqualTo('0')
    }

    @Test
    fun formattedCardIdWithRandomPositions() {
        val cardId = CardIdBuilder.createCardId(
            series = "CB79",
            startNumber = 1,
            numberFormat = "RNNNNNNNNNN",
        )
        assertThat(cardId).isNotNull()
        assertThat(cardId).hasLength(16)
        // R position gets a random digit 0-9
        assertThat(cardId!![4]).isAnyOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
    }

    @Test
    fun formattedCardIdWrongLengthReturnsNull() {
        // 4-char series needs 11-char format, giving 12
        val cardId = CardIdBuilder.createCardId(
            series = "CB79",
            startNumber = 1,
            numberFormat = "NNNNNNNNNNNN",
        )
        assertThat(cardId).isNull()
    }

    @Test
    fun formattedCardIdInvalidFormatCharsReturnsNull() {
        val cardId = CardIdBuilder.createCardId(
            series = "CB79",
            startNumber = 1,
            numberFormat = "NNNNNNNNNZZ",
        )
        assertThat(cardId).isNull()
    }

    @Test
    fun formattedCardIdNumberTooLargeForFormat() {
        // 4-char series → 11 digit tail, but number has 12 digits
        val cardId = CardIdBuilder.createCardId(
            series = "CB79",
            startNumber = 999999999999L,
            numberFormat = "NNNNNNNNNNN",
        )
        assertThat(cardId).isNull()
    }

    @Test
    fun formattedCardIdWith2CharSeries() {
        // 2-char series → tail length = 13
        val cardId = CardIdBuilder.createCardId(
            series = "CB",
            startNumber = 1,
            numberFormat = "NNNNNNNNNNNNN",
        )
        assertThat(cardId).isNotNull()
        assertThat(cardId).hasLength(16)
        assertThat(cardId).startsWith("CB")
    }

    @Test
    fun formattedCardIdHasValidLuhn() {
        val cardId = CardIdBuilder.createCardId(
            series = "CB79",
            startNumber = 42,
            numberFormat = "NNNNNNNNNNN",
        )
        assertThat(cardId).isNotNull()
        assertThat(verifyLuhn(cardId!!)).isTrue()
    }

    @Test
    fun formattedCardIdCardNumberAdded() {
        val cardId = CardIdBuilder.createCardId(
            series = "CB79",
            startNumber = 100,
            cardNumber = 5,
            numberFormat = "NNNNNNNNNNN",
        )
        assertThat(cardId).isNotNull()
        assertThat(cardId!!.substring(4, 15)).isEqualTo("00000000105")
    }

    // endregion

    // region Determinism

    @Test
    fun sameInputsProduceSameCardId() {
        val id1 = CardIdBuilder.createCardId(series = "CB79", startNumber = 100)
        val id2 = CardIdBuilder.createCardId(series = "CB79", startNumber = 100)
        assertThat(id1).isEqualTo(id2)
    }

    @Test
    fun differentStartNumbersProduceDifferentCardIds() {
        val id1 = CardIdBuilder.createCardId(series = "CB79", startNumber = 100)
        val id2 = CardIdBuilder.createCardId(series = "CB79", startNumber = 200)
        assertThat(id1).isNotEqualTo(id2)
    }

    @Test
    fun differentSeriesProduceDifferentCardIds() {
        val id1 = CardIdBuilder.createCardId(series = "AB12", startNumber = 100)
        val id2 = CardIdBuilder.createCardId(series = "CD34", startNumber = 100)
        assertThat(id1).isNotEqualTo(id2)
    }

    // endregion

    /**
     * Verifies the Luhn checksum of a hex card ID.
     * Treats A-F as 10-15 for the Luhn algorithm, matching [CardIdBuilder.completeCardId].
     */
    private fun verifyLuhn(cardId: String): Boolean {
        val length = cardId.length
        var sum = 0
        for (i in 0 until length) {
            val c = cardId[length - i - 1]
            var digit = if (c in '0'..'9') c - '0' else c - 'A'
            if (i % 2 == 1) digit *= 2
            sum += if (digit > 9) digit - 9 else digit
        }
        return sum % 10 == 0
    }
}
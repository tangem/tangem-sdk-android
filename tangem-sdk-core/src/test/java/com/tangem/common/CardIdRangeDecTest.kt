package com.tangem.common

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class CardIdRangeDecTest {

    private val testRange = CardIdRangeDec(
        start = "AF99001800554008",
        end = "AF99001800559994",
    )

    @Test
    fun testCardsInRange() {
        cardsInRange.forEach { cardId ->
            assertTrue("cardId: $cardId") { testRange!!.contains(cardId) }
        }
    }

    @Test
    fun testCardsNotInRange() {
        cardsOutOfRange.forEach {
            assertFalse { testRange!!.contains(it) }
        }
    }

    private companion object {
        val cardsInRange = listOf(
            "AF99001800554018",
            "AF99001800554027",
            "AF99001800554247",
            "AF99001800559993",
        )

        val cardsOutOfRange = listOf(
            "AF99001800513213",
            "AF99001800524027",
            "AF99001800603247",
            "AF99001800679993",
        )
    }
}
package com.tangem.common

import com.google.common.truth.Truth.assertThat
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.card.UserSettings
import com.tangem.common.core.TangemSdkError
import org.junit.Test
import java.util.Date

class CardFilterTest {

    // region Default filter

    @Test
    fun defaultFilterAllowsReleaseCards() {
        val filter = CardFilter.default()
        val card = createCard(firmwareType = FirmwareVersion.FirmwareType.Release)
        assertThat(filter.verifyCard(card)).isTrue()
    }

    @Test
    fun defaultFilterAllowsSdkCards() {
        val filter = CardFilter.default()
        val card = createCard(firmwareType = FirmwareVersion.FirmwareType.Sdk)
        assertThat(filter.verifyCard(card)).isTrue()
    }

    @Test(expected = TangemSdkError.WrongCardType::class)
    fun defaultFilterRejectsSpecialCards() {
        val filter = CardFilter.default()
        val card = createCard(firmwareType = FirmwareVersion.FirmwareType.Sprecial)
        filter.verifyCard(card)
    }

    // endregion

    // region allowedCardTypes

    @Test
    fun allowedCardTypesOnlyRelease() {
        val filter = CardFilter(allowedCardTypes = listOf(FirmwareVersion.FirmwareType.Release))
        assertThat(filter.verifyCard(createCard(firmwareType = FirmwareVersion.FirmwareType.Release))).isTrue()
    }

    @Test(expected = TangemSdkError.WrongCardType::class)
    fun allowedCardTypesRejectsSdk() {
        val filter = CardFilter(allowedCardTypes = listOf(FirmwareVersion.FirmwareType.Release))
        filter.verifyCard(createCard(firmwareType = FirmwareVersion.FirmwareType.Sdk))
    }

    @Test(expected = TangemSdkError.WrongCardType::class)
    fun allowedCardTypesEmptyRejectsAll() {
        val filter = CardFilter(allowedCardTypes = emptyList())
        filter.verifyCard(createCard(firmwareType = FirmwareVersion.FirmwareType.Release))
    }

    @Test
    fun allowedCardTypesAllTypes() {
        val filter = CardFilter(
            allowedCardTypes = FirmwareVersion.FirmwareType.values().toList(),
        )
        FirmwareVersion.FirmwareType.values().forEach { type ->
            assertThat(filter.verifyCard(createCard(firmwareType = type))).isTrue()
        }
    }

    // endregion

    // region maxFirmwareVersion

    @Test
    fun maxFirmwareVersionAllowsLower() {
        val filter = CardFilter(maxFirmwareVersion = FirmwareVersion(5, 0))
        val card = createCard(major = 4, minor = 33)
        assertThat(filter.verifyCard(card)).isTrue()
    }

    @Test
    fun maxFirmwareVersionAllowsEqual() {
        val filter = CardFilter(maxFirmwareVersion = FirmwareVersion(4, 33))
        val card = createCard(major = 4, minor = 33)
        assertThat(filter.verifyCard(card)).isTrue()
    }

    @Test(expected = TangemSdkError.WrongCardType::class)
    fun maxFirmwareVersionRejectsHigher() {
        val filter = CardFilter(maxFirmwareVersion = FirmwareVersion(4, 0))
        val card = createCard(major = 4, minor = 33)
        filter.verifyCard(card)
    }

    @Test
    fun maxFirmwareVersionNullAllowsAll() {
        val filter = CardFilter(maxFirmwareVersion = null)
        val card = createCard(major = 99, minor = 99)
        assertThat(filter.verifyCard(card)).isTrue()
    }

    // endregion

    // region batchIdFilter

    @Test
    fun batchIdFilterAllowMatchingBatch() {
        val filter = CardFilter(
            batchIdFilter = CardFilter.Companion.ItemFilter.Allow(setOf("CB79", "AB12")),
        )
        assertThat(filter.verifyCard(createCard(batchId = "CB79"))).isTrue()
    }

    @Test(expected = TangemSdkError.WrongCardType::class)
    fun batchIdFilterAllowRejectsNonMatchingBatch() {
        val filter = CardFilter(
            batchIdFilter = CardFilter.Companion.ItemFilter.Allow(setOf("CB79")),
        )
        filter.verifyCard(createCard(batchId = "XX00"))
    }

    @Test(expected = TangemSdkError.WrongCardType::class)
    fun batchIdFilterDenyRejectsMatchingBatch() {
        val filter = CardFilter(
            batchIdFilter = CardFilter.Companion.ItemFilter.Deny(setOf("CB79")),
        )
        filter.verifyCard(createCard(batchId = "CB79"))
    }

    @Test
    fun batchIdFilterDenyAllowsNonMatchingBatch() {
        val filter = CardFilter(
            batchIdFilter = CardFilter.Companion.ItemFilter.Deny(setOf("CB79")),
        )
        assertThat(filter.verifyCard(createCard(batchId = "XX00"))).isTrue()
    }

    @Test
    fun batchIdFilterNullAllowsAll() {
        val filter = CardFilter(batchIdFilter = null)
        assertThat(filter.verifyCard(createCard(batchId = "ANYTHING"))).isTrue()
    }

    // endregion

    // region issuerFilter

    @Test
    fun issuerFilterAllowMatchingIssuer() {
        val filter = CardFilter(
            issuerFilter = CardFilter.Companion.ItemFilter.Allow(setOf("TANGEM AG")),
        )
        assertThat(filter.verifyCard(createCard(issuerName = "TANGEM AG"))).isTrue()
    }

    @Test(expected = TangemSdkError.WrongCardType::class)
    fun issuerFilterAllowRejectsNonMatchingIssuer() {
        val filter = CardFilter(
            issuerFilter = CardFilter.Companion.ItemFilter.Allow(setOf("TANGEM AG")),
        )
        filter.verifyCard(createCard(issuerName = "OTHER"))
    }

    @Test(expected = TangemSdkError.WrongCardType::class)
    fun issuerFilterDenyRejectsMatchingIssuer() {
        val filter = CardFilter(
            issuerFilter = CardFilter.Companion.ItemFilter.Deny(setOf("BAD_ISSUER")),
        )
        filter.verifyCard(createCard(issuerName = "BAD_ISSUER"))
    }

    @Test
    fun issuerFilterDenyAllowsNonMatchingIssuer() {
        val filter = CardFilter(
            issuerFilter = CardFilter.Companion.ItemFilter.Deny(setOf("BAD_ISSUER")),
        )
        assertThat(filter.verifyCard(createCard(issuerName = "TANGEM AG"))).isTrue()
    }

    // endregion

    // region cardIdFilter

    @Test
    fun cardIdFilterAllowMatchingCardId() {
        val filter = CardFilter(
            cardIdFilter = CardFilter.Companion.CardIdFilter.Allow(setOf("CB79000000018201")),
        )
        assertThat(filter.verifyCard(createCard(cardId = "CB79000000018201"))).isTrue()
    }

    @Test(expected = TangemSdkError.WrongCardType::class)
    fun cardIdFilterAllowRejectsNonMatchingCardId() {
        val filter = CardFilter(
            cardIdFilter = CardFilter.Companion.CardIdFilter.Allow(setOf("CB79000000018201")),
        )
        filter.verifyCard(createCard(cardId = "CB79000000099999"))
    }

    @Test(expected = TangemSdkError.WrongCardType::class)
    fun cardIdFilterDenyRejectsMatchingCardId() {
        val filter = CardFilter(
            cardIdFilter = CardFilter.Companion.CardIdFilter.Deny(setOf("CB79000000018201")),
        )
        filter.verifyCard(createCard(cardId = "CB79000000018201"))
    }

    @Test
    fun cardIdFilterDenyAllowsNonMatchingCardId() {
        val filter = CardFilter(
            cardIdFilter = CardFilter.Companion.CardIdFilter.Deny(setOf("CB79000000018201")),
        )
        assertThat(filter.verifyCard(createCard(cardId = "CB79000000099999"))).isTrue()
    }

    @Test
    fun cardIdFilterAllowWithRangeMatchesCardInRange() {
        val range = CardIdRange("CB79000000010000", "CB79000000020000")!!
        val filter = CardFilter(
            cardIdFilter = CardFilter.Companion.CardIdFilter.Allow(
                items = emptySet(),
                ranges = listOf(range),
            ),
        )
        assertThat(filter.verifyCard(createCard(cardId = "CB79000000015000"))).isTrue()
    }

    @Test(expected = TangemSdkError.WrongCardType::class)
    fun cardIdFilterAllowWithRangeRejectsCardOutOfRange() {
        val range = CardIdRange("CB79000000010000", "CB79000000020000")!!
        val filter = CardFilter(
            cardIdFilter = CardFilter.Companion.CardIdFilter.Allow(
                items = emptySet(),
                ranges = listOf(range),
            ),
        )
        filter.verifyCard(createCard(cardId = "CB79000000090000"))
    }

    @Test(expected = TangemSdkError.WrongCardType::class)
    fun cardIdFilterDenyWithRangeRejectsCardInRange() {
        val range = CardIdRange("CB79000000010000", "CB79000000020000")!!
        val filter = CardFilter(
            cardIdFilter = CardFilter.Companion.CardIdFilter.Deny(
                items = emptySet(),
                ranges = listOf(range),
            ),
        )
        filter.verifyCard(createCard(cardId = "CB79000000015000"))
    }

    // endregion

    // region localizedDescription

    @Test
    fun customLocalizedDescriptionInError() {
        val customMessage = "This card is not supported"
        val filter = CardFilter(
            allowedCardTypes = emptyList(),
            localizedDescription = customMessage,
        )
        try {
            filter.verifyCard(createCard())
        } catch (e: TangemSdkError.WrongCardType) {
            assertThat(e.customMessage).isEqualTo(customMessage)
            return
        }
        throw AssertionError("Expected WrongCardType to be thrown")
    }

    @Test
    fun defaultLocalizedDescriptionIsErrorCode() {
        val filter = CardFilter(allowedCardTypes = emptyList())
        try {
            filter.verifyCard(createCard())
        } catch (e: TangemSdkError.WrongCardType) {
            assertThat(e.customMessage).isEqualTo("50006")
            return
        }
        throw AssertionError("Expected WrongCardType to be thrown")
    }

    // endregion

    // region Combined filters

    @Test
    fun combinedFiltersAllPass() {
        val filter = CardFilter(
            allowedCardTypes = listOf(FirmwareVersion.FirmwareType.Release),
            batchIdFilter = CardFilter.Companion.ItemFilter.Allow(setOf("CB79")),
            issuerFilter = CardFilter.Companion.ItemFilter.Allow(setOf("TANGEM AG")),
            cardIdFilter = CardFilter.Companion.CardIdFilter.Allow(setOf("CB79000000018201")),
            maxFirmwareVersion = FirmwareVersion(5, 0),
        )
        val card = createCard(
            cardId = "CB79000000018201",
            batchId = "CB79",
            issuerName = "TANGEM AG",
            major = 4,
            minor = 12,
            firmwareType = FirmwareVersion.FirmwareType.Release,
        )
        assertThat(filter.verifyCard(card)).isTrue()
    }

    @Test(expected = TangemSdkError.WrongCardType::class)
    fun combinedFiltersFailsOnFirstMismatch() {
        val filter = CardFilter(
            allowedCardTypes = listOf(FirmwareVersion.FirmwareType.Release),
            maxFirmwareVersion = FirmwareVersion(3, 0),
            batchIdFilter = CardFilter.Companion.ItemFilter.Allow(setOf("XX00")),
        )
        filter.verifyCard(createCard(major = 4, minor = 12))
    }

    // endregion

    // region ItemFilter

    @Test
    fun itemFilterAllowContainsItem() {
        val filter = CardFilter.Companion.ItemFilter.Allow(setOf("A", "B", "C"))
        assertThat(filter.isAllowed("A")).isTrue()
        assertThat(filter.isAllowed("B")).isTrue()
        assertThat(filter.isAllowed("D")).isFalse()
    }

    @Test
    fun itemFilterDenyContainsItem() {
        val filter = CardFilter.Companion.ItemFilter.Deny(setOf("A", "B"))
        assertThat(filter.isAllowed("A")).isFalse()
        assertThat(filter.isAllowed("C")).isTrue()
    }

    @Test
    fun itemFilterAllowEmptySetDeniesAll() {
        val filter = CardFilter.Companion.ItemFilter.Allow(emptySet())
        assertThat(filter.isAllowed("anything")).isFalse()
    }

    @Test
    fun itemFilterDenyEmptySetAllowsAll() {
        val filter = CardFilter.Companion.ItemFilter.Deny(emptySet())
        assertThat(filter.isAllowed("anything")).isTrue()
    }

    // endregion

    // region CardIdFilter

    @Test
    fun cardIdFilterAllowByItemsOrRanges() {
        val range = CardIdRange("AB12000000001000", "AB12000000002000")!!
        val filter = CardFilter.Companion.CardIdFilter.Allow(
            items = setOf("CB79000000018201"),
            ranges = listOf(range),
        )
        assertThat(filter.isAllowed("CB79000000018201")).isTrue()
        assertThat(filter.isAllowed("AB12000000001500")).isTrue()
        assertThat(filter.isAllowed("XX00000000000001")).isFalse()
    }

    @Test
    fun cardIdFilterDenyByItemsOrRanges() {
        val range = CardIdRange("AB12000000001000", "AB12000000002000")!!
        val filter = CardFilter.Companion.CardIdFilter.Deny(
            items = setOf("CB79000000018201"),
            ranges = listOf(range),
        )
        assertThat(filter.isAllowed("CB79000000018201")).isFalse()
        assertThat(filter.isAllowed("AB12000000001500")).isFalse()
        assertThat(filter.isAllowed("XX00000000000001")).isTrue()
    }

    // endregion

    // region CardIdRange

    @Test
    fun cardIdRangeCreation() {
        val range = CardIdRange("CB79000000010000", "CB79000000020000")
        assertThat(range).isNotNull()
    }

    @Test
    fun cardIdRangeNullForMismatchedBatches() {
        val range = CardIdRange("CB79000000010000", "AB12000000020000")
        assertThat(range).isNull()
    }

    @Test
    fun cardIdRangeContainsCardInRange() {
        val range = CardIdRange("CB79000000010000", "CB79000000020000")!!
        assertThat(range.contains("CB79000000015000")).isTrue()
    }

    @Test
    fun cardIdRangeContainsBoundaryStart() {
        val range = CardIdRange("CB79000000010000", "CB79000000020000")!!
        assertThat(range.contains("CB79000000010000")).isTrue()
    }

    @Test
    fun cardIdRangeContainsBoundaryEnd() {
        val range = CardIdRange("CB79000000010000", "CB79000000020000")!!
        assertThat(range.contains("CB79000000020000")).isTrue()
    }

    @Test
    fun cardIdRangeDoesNotContainOutOfRange() {
        val range = CardIdRange("CB79000000010000", "CB79000000020000")!!
        assertThat(range.contains("CB79000000030000")).isFalse()
    }

    @Test
    fun cardIdRangeDoesNotContainDifferentBatch() {
        val range = CardIdRange("CB79000000010000", "CB79000000020000")!!
        assertThat(range.contains("AB12000000015000")).isFalse()
    }

    // endregion

    // region CardIdRangeDec

    @Test
    fun cardIdRangeDecCreation() {
        val range = CardIdRangeDec("CB79000000010000", "CB79000000020000")
        assertThat(range).isNotNull()
    }

    @Test
    fun cardIdRangeDecNullForInvalidHex() {
        val range = CardIdRangeDec("ZZZZ", "XXXX")
        assertThat(range).isNull()
    }

    @Test
    fun cardIdRangeDecContainsCardInRange() {
        val range = CardIdRangeDec("CB79000000010000", "CB79000000020000")!!
        assertThat(range.contains("CB79000000015000")).isTrue()
    }

    @Test
    fun cardIdRangeDecDoesNotContainOutOfRange() {
        val range = CardIdRangeDec("CB79000000010000", "CB79000000020000")!!
        assertThat(range.contains("CB79000000030000")).isFalse()
    }

    @Test
    fun cardIdRangeDecContainsBoundaries() {
        val range = CardIdRangeDec("CB79000000010000", "CB79000000020000")!!
        assertThat(range.contains("CB79000000010000")).isTrue()
        assertThat(range.contains("CB79000000020000")).isTrue()
    }

    @Test
    fun cardIdRangeDecReturnsFalseForInvalidCardId() {
        val range = CardIdRangeDec("CB79000000010000", "CB79000000020000")!!
        assertThat(range.contains("NOT_HEX")).isFalse()
    }

    // endregion

    // region List<CardIdRange>.contains extension

    @Test
    fun cardIdRangeListContainsMatchInAnyRange() {
        val range1 = CardIdRange("CB79000000010000", "CB79000000020000")!!
        val range2 = CardIdRange("CB79000000050000", "CB79000000060000")!!
        val ranges = listOf(range1, range2)
        assertThat(ranges.contains("CB79000000015000")).isTrue()
        assertThat(ranges.contains("CB79000000055000")).isTrue()
    }

    @Test
    fun cardIdRangeListDoesNotContainIfNoRangeMatches() {
        val range1 = CardIdRange("CB79000000010000", "CB79000000020000")!!
        val ranges = listOf(range1)
        assertThat(ranges.contains("CB79000000030000")).isFalse()
    }

    @Test
    fun cardIdRangeEmptyListContainsNothing() {
        val ranges = emptyList<CardIdRange>()
        assertThat(ranges.contains("CB79000000015000")).isFalse()
    }

    // endregion

    private fun createCard(
        cardId: String = "CB79000000018201",
        batchId: String = "CB79",
        issuerName: String = "TANGEM AG",
        major: Int = 4,
        minor: Int = 12,
        firmwareType: FirmwareVersion.FirmwareType = FirmwareVersion.FirmwareType.Release,
    ): Card = Card(
        cardId = cardId,
        batchId = batchId,
        cardPublicKey = ByteArray(0),
        firmwareVersion = FirmwareVersion(major, minor, 0, firmwareType),
        manufacturer = Card.Manufacturer("TANGEM", Date(), null),
        issuer = Card.Issuer(issuerName, ByteArray(0)),
        settings = Card.Settings(
            securityDelay = 0,
            maxWalletsCount = 0,
            mask = Card.SettingsMask(0),
        ),
        userSettings = UserSettings(
            isUserCodeRecoveryAllowed = false,
            isPINRequired = false,
            isNDEFDisabled = false,
        ),
        linkedTerminalStatus = Card.LinkedTerminalStatus.None,
        isAccessCodeSet = false,
        isPasscodeSet = false,
        supportedCurves = emptyList(),
        wallets = emptyList(),
    )
}
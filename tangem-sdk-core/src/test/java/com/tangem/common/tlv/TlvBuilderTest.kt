package com.tangem.common.tlv

import com.google.common.truth.Truth.assertThat
import com.tangem.common.UserCode
import com.tangem.common.UserCodeType
import com.tangem.common.card.Card
import com.tangem.common.card.Card.SettingsMask
import com.tangem.common.card.Card.SettingsMask.Code
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.card.UserSettings
import org.junit.Test
import java.util.Date

internal class TlvBuilderTest {

    @Test
    fun `appendPinIfNeeded adds tlv if pin is not default`() {
        val builder = TlvBuilder()
        val card = emptyCardFV434
        val userCode = UserCode(UserCodeType.AccessCode, "111111")

        val result = builder.appendPinIfNeeded(TlvTag.Pin, userCode, card)
        assertThat(result).isSameInstanceAs(builder)
        assertThat(builder.serialize()).isNotEmpty()
    }

    @Test(expected = com.tangem.common.core.TangemSdkError.EncodingFailed::class)
    fun `appendPinIfNeeded throw error if tag is not Pin or Pin2`() {
        val builder = TlvBuilder()
        val card = emptyCardFV434
        val userCode = UserCode(UserCodeType.AccessCode, ByteArray(0))

        builder.appendPinIfNeeded(TlvTag.CardId, userCode, card)
    }

    @Test
    fun `appendPinIfNeeded doesn't add tlv if pin default and firmware higher 4,34`() {
        val builder = TlvBuilder()
        val card = emptyCardFV434
        builder.append(TlvTag.CardId, card.cardId) // added some tlv to make sure builder works
        val expectedData = builder.serialize()

        val defaultPin = UserCode(UserCodeType.AccessCode)

        val result = builder.appendPinIfNeeded(TlvTag.Pin, defaultPin, card)
        assertThat(result).isSameInstanceAs(builder)
        assertThat(builder.serialize()).isEqualTo(expectedData)
    }

    @Test
    fun `always append Pin for firmware version less 4,34`() {
        val builderExpected = TlvBuilder()
        val card = emptyCardFV433
        val defaultPin = UserCode(UserCodeType.AccessCode)

        builderExpected.append(TlvTag.Pin, defaultPin.value)
        val expectedData = builderExpected.serialize()

        val builder = TlvBuilder()
        val result = builder.appendPinIfNeeded(TlvTag.Pin, defaultPin, card)
        assertThat(result).isSameInstanceAs(builder)
        assertThat(builder.serialize()).isEqualTo(expectedData)
    }

    private companion object {
        private val emptyCardSetting = Card.Settings(
            securityDelay = 0,
            maxWalletsCount = 0,
            mask = SettingsMask(Code.AllowHDWallets.value),
        )

        private val emptyCardFV433 = Card(
            cardId = "1",
            batchId = "",
            cardPublicKey = ByteArray(0),
            firmwareVersion = FirmwareVersion(4, 33),
            manufacturer = Card.Manufacturer("", Date(), null),
            issuer = Card.Issuer("", ByteArray(0)),
            settings = emptyCardSetting,
            userSettings = UserSettings(false),
            linkedTerminalStatus = Card.LinkedTerminalStatus.None,
            isAccessCodeSet = false,
            isPasscodeSet = false,
            supportedCurves = emptyList(),
            wallets = emptyList(),
        )

        private val emptyCardFV434 = Card(
            cardId = "1",
            batchId = "",
            cardPublicKey = ByteArray(0),
            firmwareVersion = FirmwareVersion(4, 34),
            manufacturer = Card.Manufacturer("", Date(), null),
            issuer = Card.Issuer("", ByteArray(0)),
            settings = emptyCardSetting,
            userSettings = UserSettings(false),
            linkedTerminalStatus = Card.LinkedTerminalStatus.None,
            isAccessCodeSet = false,
            isPasscodeSet = false,
            supportedCurves = emptyList(),
            wallets = emptyList(),
        )
    }
}
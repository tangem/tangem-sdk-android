package com.tangem.common.tlv

import com.google.common.truth.Truth.assertThat
import com.tangem.common.card.*
import com.tangem.common.card.Card.SettingsMask
import com.tangem.common.card.Card.SettingsMask.Code
import com.tangem.common.core.AccessLevel
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.hexToBytes
import com.tangem.operations.personalization.entities.ProductMask
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import java.util.Calendar
import java.util.Date

internal class TlvBuilderTest {

    // region append & serialize basics

    @Test
    fun `serialize empty builder returns empty array`() {
        val builder = TlvBuilder()
        assertThat(builder.serialize()).isEmpty()
    }

    @Test
    fun `append null value is skipped`() {
        val builder = TlvBuilder()
        builder.append(TlvTag.CardId, null as String?)
        assertThat(builder.serialize()).isEmpty()
    }

    @Test
    fun `append single tag produces correct TLV`() {
        val builder = TlvBuilder()
        builder.append(TlvTag.CardId, "CB22000000027374")
        val bytes = builder.serialize()

        // Deserialize and verify
        val tlvs = Tlv.deserialize(bytes)
        assertThat(tlvs).isNotNull()
        assertThat(tlvs).hasSize(1)
        assertThat(tlvs!![0].tag).isEqualTo(TlvTag.CardId)
        assertThat(tlvs[0].value).isEqualTo("CB22000000027374".hexToBytes())
    }

    @Test
    fun `append multiple tags serializes all in order`() {
        val builder = TlvBuilder()
        builder.append(TlvTag.CardId, "CB22000000027374")
        builder.append(TlvTag.WalletIndex, 3)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)
        assertThat(tlvs).isNotNull()
        assertThat(tlvs).hasSize(2)
        assertThat(tlvs!![0].tag).isEqualTo(TlvTag.CardId)
        assertThat(tlvs[1].tag).isEqualTo(TlvTag.WalletIndex)
    }

    // endregion

    // region HexString

    @Test
    fun `encode HexString tag`() {
        val builder = TlvBuilder()
        builder.append(TlvTag.CardId, "AABB")
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        assertThat(tlvs[0].value).isEqualTo(byteArrayOf(0xAA.toByte(), 0xBB.toByte()))
    }

    @Test
    fun `encode HexString with wrong type throws`() {
        val builder = TlvBuilder()
        assertThrows<TangemSdkError.EncodingFailedTypeMismatch> {
            builder.append(TlvTag.CardId, 123)
        }
    }

    // endregion

    // region Utf8String

    @Test
    fun `encode Utf8String tag`() {
        val builder = TlvBuilder()
        builder.append(TlvTag.ManufacturerName, "Tangem")
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        assertThat(String(tlvs[0].value)).isEqualTo("Tangem")
    }

    // endregion

    // region Integer types

    @Test
    fun `encode Uint8 tag`() {
        val builder = TlvBuilder()
        builder.append(TlvTag.WalletIndex, 5)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        assertThat(tlvs[0].value).hasLength(1)
        assertThat(tlvs[0].value[0].toInt() and 0xFF).isEqualTo(5)
    }

    @Test
    fun `encode Uint16 tag`() {
        val builder = TlvBuilder()
        builder.append(TlvTag.PauseBeforePin2, 300)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        assertThat(tlvs[0].value).hasLength(2)
        val decoded = (tlvs[0].value[0].toInt() and 0xFF shl 8) or (tlvs[0].value[1].toInt() and 0xFF)
        assertThat(decoded).isEqualTo(300)
    }

    @Test
    fun `encode Uint32 tag`() {
        val builder = TlvBuilder()
        builder.append(TlvTag.MaxSignatures, 100_000)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        assertThat(tlvs[0].value).hasLength(4)
    }

    @Test
    fun `encode Uint8 with wrong type throws`() {
        val builder = TlvBuilder()
        assertThrows<TangemSdkError.EncodingFailedTypeMismatch> {
            builder.append(TlvTag.WalletIndex, "not an int")
        }
    }

    // endregion

    // region BoolValue

    @Test
    fun `encode BoolValue true`() {
        val builder = TlvBuilder()
        builder.append(TlvTag.IsActivated, true)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        assertThat(tlvs[0].value).isEqualTo(byteArrayOf(1))
    }

    @Test
    fun `encode BoolValue false`() {
        val builder = TlvBuilder()
        builder.append(TlvTag.IsActivated, false)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        assertThat(tlvs[0].value).isEqualTo(byteArrayOf(0))
    }

    // endregion

    // region ByteArray

    @Test
    fun `encode ByteArray tag`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val builder = TlvBuilder()
        builder.append(TlvTag.Pin, data)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        assertThat(tlvs[0].value).isEqualTo(data)
    }

    // endregion

    // region EllipticCurve

    @Test
    fun `encode EllipticCurve Secp256k1`() {
        val builder = TlvBuilder()
        builder.append(TlvTag.CurveId, EllipticCurve.Secp256k1)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        val curveString = String(tlvs[0].value)
        assertThat(curveString).isEqualTo("secp256k1")
    }

    @Test
    fun `encode EllipticCurve Ed25519`() {
        val builder = TlvBuilder()
        builder.append(TlvTag.CurveId, EllipticCurve.Ed25519)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        val curveString = String(tlvs[0].value)
        assertThat(curveString).isEqualTo("ed25519")
    }

    // endregion

    // region DateTime

    @Test
    fun `encode DateTime tag`() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2023)
            set(Calendar.MONTH, Calendar.MARCH)
            set(Calendar.DAY_OF_MONTH, 15)
        }
        val builder = TlvBuilder()
        builder.append(TlvTag.ManufactureDateTime, calendar.time)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        val value = tlvs[0].value
        // DateTime = year(2 bytes) + month(1 byte) + day(1 byte)
        assertThat(value).hasLength(4)
        val year = (value[0].toInt() and 0xFF shl 8) or (value[1].toInt() and 0xFF)
        val month = value[2].toInt() and 0xFF
        val day = value[3].toInt() and 0xFF
        assertThat(year).isEqualTo(2023)
        assertThat(month).isEqualTo(3)
        assertThat(day).isEqualTo(15)
    }

    // endregion

    // region ProductMask

    @Test
    fun `encode ProductMask tag`() {
        val mask = ProductMask(ProductMask.Code.Note.value)
        val builder = TlvBuilder()
        builder.append(TlvTag.ProductMask, mask)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        assertThat(tlvs[0].value).hasLength(1)
        assertThat(tlvs[0].value[0].toInt() and 0xFF).isEqualTo(ProductMask.Code.Note.value)
    }

    // endregion

    // region SigningMethod

    @Test
    fun `encode SigningMethod tag`() {
        val method = SigningMethod(0)
        val builder = TlvBuilder()
        builder.append(TlvTag.SigningMethod, method)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        assertThat(tlvs[0].value).hasLength(1)
    }

    // endregion

    // region SettingsMask

    @Test
    fun `encode CardSettingsMask with small value uses 2 bytes`() {
        val mask = SettingsMask(Code.IsReusable.value)
        val builder = TlvBuilder()
        builder.append(TlvTag.SettingsMask, mask)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        assertThat(tlvs[0].value).hasLength(2)
    }

    @Test
    fun `encode CardSettingsMask with large value uses 4 bytes`() {
        // Use a value that sets bits in upper 2 bytes
        val mask = SettingsMask(0x00010000)
        val builder = TlvBuilder()
        builder.append(TlvTag.SettingsMask, mask)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        assertThat(tlvs[0].value).hasLength(4)
    }

    // endregion

    // region UserSettingsMask

    @Test
    fun `encode UserSettingsMask tag uses 4 bytes`() {
        val mask = UserSettingsMask(1)
        val builder = TlvBuilder()
        builder.append(TlvTag.UserSettingsMask, mask)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        assertThat(tlvs[0].value).hasLength(4)
    }

    // endregion

    // region BackupStatus

    @Test
    fun `encode BackupStatus tag`() {
        val builder = TlvBuilder()
        builder.append(TlvTag.BackupStatus, Card.BackupRawStatus.Active)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        assertThat(tlvs[0].tag).isEqualTo(TlvTag.BackupStatus)
    }

    // endregion

    // region AccessLevel

    @Test
    fun `encode AccessLevel tag`() {
        val builder = TlvBuilder()
        builder.append(TlvTag.AccessLevel, AccessLevel.FileOwner)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        assertThat(tlvs[0].value).hasLength(1)
    }

    // endregion

    // region roundtrip encode-decode

    @Test
    fun `roundtrip encode-decode HexString`() {
        val cardId = "CB22000000027374"
        val builder = TlvBuilder()
        builder.append(TlvTag.CardId, cardId)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        val decoder = TlvDecoder(tlvs)
        val decoded: String = decoder.decode(TlvTag.CardId)
        assertThat(decoded).isEqualTo(cardId)
    }

    @Test
    fun `roundtrip encode-decode Utf8String`() {
        val name = "Tangem AG"
        val builder = TlvBuilder()
        builder.append(TlvTag.ManufacturerName, name)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        val decoder = TlvDecoder(tlvs)
        val decoded: String = decoder.decode(TlvTag.ManufacturerName)
        assertThat(decoded).isEqualTo(name)
    }

    @Test
    fun `roundtrip encode-decode EllipticCurve`() {
        val builder = TlvBuilder()
        builder.append(TlvTag.CurveId, EllipticCurve.Secp256r1)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        val decoder = TlvDecoder(tlvs)
        val decoded: EllipticCurve = decoder.decode(TlvTag.CurveId)
        assertThat(decoded).isEqualTo(EllipticCurve.Secp256r1)
    }

    @Test
    fun `roundtrip encode-decode BoolValue`() {
        val builder = TlvBuilder()
        builder.append(TlvTag.TerminalIsLinked, true)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        val decoder = TlvDecoder(tlvs)
        val decoded: Boolean = decoder.decode(TlvTag.TerminalIsLinked)
        assertThat(decoded).isTrue()
    }

    @Test
    fun `roundtrip encode-decode Uint16`() {
        val builder = TlvBuilder()
        builder.append(TlvTag.PauseBeforePin2, 500)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        val decoder = TlvDecoder(tlvs)
        val decoded: Int = decoder.decode(TlvTag.PauseBeforePin2)
        assertThat(decoded).isEqualTo(500)
    }

    @Test
    fun `roundtrip encode-decode Uint32`() {
        val builder = TlvBuilder()
        builder.append(TlvTag.MaxSignatures, 999_999)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        val decoder = TlvDecoder(tlvs)
        val decoded: Int = decoder.decode(TlvTag.MaxSignatures)
        assertThat(decoded).isEqualTo(999_999)
    }

    @Test
    fun `roundtrip encode-decode AccessLevel`() {
        val builder = TlvBuilder()
        builder.append(TlvTag.AccessLevel, AccessLevel.FileOwner)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        val decoder = TlvDecoder(tlvs)
        val decoded: AccessLevel = decoder.decode(TlvTag.AccessLevel)
        assertThat(decoded).isEqualTo(AccessLevel.FileOwner)
    }

    @Test
    fun `roundtrip encode-decode DateTime`() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2024)
            set(Calendar.MONTH, Calendar.DECEMBER)
            set(Calendar.DAY_OF_MONTH, 25)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val builder = TlvBuilder()
        builder.append(TlvTag.ManufactureDateTime, calendar.time)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        val decoder = TlvDecoder(tlvs)
        val decoded: Date = decoder.decode(TlvTag.ManufactureDateTime)

        val decodedCal = Calendar.getInstance().apply { time = decoded }
        assertThat(decodedCal.get(Calendar.YEAR)).isEqualTo(2024)
        assertThat(decodedCal.get(Calendar.MONTH)).isEqualTo(Calendar.DECEMBER)
        assertThat(decodedCal.get(Calendar.DAY_OF_MONTH)).isEqualTo(25)
    }

    // endregion

    // region complex scenario

    @Test
    fun `build multi-tag payload and verify byte output`() {
        val builder = TlvBuilder()
        builder.append(TlvTag.CardId, "CB22000000027374")
        builder.append(TlvTag.IsActivated, true)
        builder.append(TlvTag.WalletIndex, 0)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        assertThat(tlvs).hasSize(3)
        assertThat(tlvs[0].tag).isEqualTo(TlvTag.CardId)
        assertThat(tlvs[0].value).isEqualTo("CB22000000027374".hexToBytes())
        assertThat(tlvs[1].tag).isEqualTo(TlvTag.IsActivated)
        assertThat(tlvs[1].value).isEqualTo(byteArrayOf(1))
        assertThat(tlvs[2].tag).isEqualTo(TlvTag.WalletIndex)
        assertThat(tlvs[2].value[0].toInt() and 0xFF).isEqualTo(0)
    }

    @Test
    fun `null values are skipped in mixed append`() {
        val builder = TlvBuilder()
        builder.append(TlvTag.CardId, "AABB")
        builder.append(TlvTag.ManufacturerName, null as String?)
        builder.append(TlvTag.IsActivated, true)
        val bytes = builder.serialize()

        val tlvs = Tlv.deserialize(bytes)!!
        assertThat(tlvs).hasSize(2)
        assertThat(tlvs[0].tag).isEqualTo(TlvTag.CardId)
        assertThat(tlvs[1].tag).isEqualTo(TlvTag.IsActivated)
    }

    // endregion

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

        private val emptyCardFV434 = Card(
            cardId = "1",
            batchId = "",
            cardPublicKey = ByteArray(0),
            firmwareVersion = FirmwareVersion(4, 34),
            manufacturer = Card.Manufacturer("", Date(), null),
            issuer = Card.Issuer("", ByteArray(0)),
            settings = emptyCardSetting,
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
}
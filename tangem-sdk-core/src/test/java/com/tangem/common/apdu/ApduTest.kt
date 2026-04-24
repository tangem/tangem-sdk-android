package com.tangem.common.apdu

import com.google.common.truth.Truth.assertThat
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.extensions.toHexString
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvTag
import org.junit.Test
import org.junit.jupiter.api.assertThrows

class ApduTest {

    // region Apdu object

    @Test
    fun `Apdu build concatenates hex strings`() {
        val result = Apdu.build(Apdu.SELECT, Apdu.TANGEM_WALLET_AID)
        val expected = (Apdu.SELECT + Apdu.TANGEM_WALLET_AID).hexToBytes()
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `Apdu build single string`() {
        val result = Apdu.build("AABB")
        assertThat(result).isEqualTo(byteArrayOf(0xAA.toByte(), 0xBB.toByte()))
    }

    @Test
    fun `Apdu build empty strings`() {
        val result = Apdu.build("")
        assertThat(result).isEmpty()
    }

    @Test
    fun `Apdu SELECT constant is correct`() {
        assertThat(Apdu.SELECT).isEqualTo("00A4040008")
    }

    @Test
    fun `Apdu TANGEM_WALLET_AID constant is correct`() {
        assertThat(Apdu.TANGEM_WALLET_AID).isEqualTo("A000000812010208")
    }

    // endregion

    // region CommandApdu

    @Test
    fun `CommandApdu header bytes are correct`() {
        val apdu = CommandApdu(
            ins = Instruction.Read.code,
            tlvs = byteArrayOf(),
            p1 = 0,
            p2 = 0,
        )
        val data = apdu.apduData
        // CLA=0x00, INS=0xF2, P1=0x00, P2=0x00
        assertThat(data[0]).isEqualTo(0x00.toByte())
        assertThat(data[1]).isEqualTo(Instruction.Read.code.toByte())
        assertThat(data[2]).isEqualTo(0x00.toByte())
        assertThat(data[3]).isEqualTo(0x00.toByte())
    }

    @Test
    fun `CommandApdu with empty tlvs has 4 byte header only`() {
        val apdu = CommandApdu(
            ins = Instruction.Read.code,
            tlvs = byteArrayOf(),
            p1 = 0,
            p2 = 0,
        )
        // No data → no LC field → just CLA INS P1 P2
        assertThat(apdu.apduData).hasLength(4)
    }

    @Test
    fun `CommandApdu with data includes extended LC`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val apdu = CommandApdu(
            ins = Instruction.Read.code,
            tlvs = payload,
            p1 = 0,
            p2 = 0,
        )
        val data = apdu.apduData
        // Header(4) + extended LC(3: 0x00, sizeHigh, sizeLow) + payload(3) = 10
        assertThat(data).hasLength(10)
        // Extended LC: 0x00, 0x00, 0x03
        assertThat(data[4]).isEqualTo(0x00.toByte())
        assertThat(data[5]).isEqualTo(0x00.toByte())
        assertThat(data[6]).isEqualTo(0x03.toByte())
        // Payload
        assertThat(data[7]).isEqualTo(0x01.toByte())
        assertThat(data[8]).isEqualTo(0x02.toByte())
        assertThat(data[9]).isEqualTo(0x03.toByte())
    }

    @Test
    fun `CommandApdu with LE appends extended LE bytes`() {
        val apdu = CommandApdu(
            ins = Instruction.Read.code,
            tlvs = byteArrayOf(),
            p1 = 0,
            p2 = 0,
            le = 256,
        )
        val data = apdu.apduData
        // Header(4) + LE(2: 0x01, 0x00)
        assertThat(data).hasLength(6)
        assertThat(data[4]).isEqualTo(0x01.toByte())
        assertThat(data[5]).isEqualTo(0x00.toByte())
    }

    @Test
    fun `CommandApdu with data and LE`() {
        val payload = byteArrayOf(0xFF.toByte())
        val apdu = CommandApdu(
            ins = Instruction.Sign.code,
            tlvs = payload,
            p1 = 0,
            p2 = 0,
            le = 512,
        )
        val data = apdu.apduData
        // Header(4) + LC(3) + payload(1) + LE(2) = 10
        assertThat(data).hasLength(10)
        // LE at end: 512 = 0x02, 0x00
        assertThat(data[8]).isEqualTo(0x02.toByte())
        assertThat(data[9]).isEqualTo(0x00.toByte())
    }

    @Test
    fun `CommandApdu Instruction constructor sets p1 p2 to zero`() {
        val tlvs = byteArrayOf(0x10, 0x01, 0x00)
        val apdu = CommandApdu(Instruction.Read, tlvs)
        val data = apdu.apduData
        assertThat(data[2]).isEqualTo(0x00.toByte()) // p1
        assertThat(data[3]).isEqualTo(0x00.toByte()) // p2
    }

    @Test
    fun `CommandApdu custom p1 and p2`() {
        val apdu = CommandApdu(
            ins = Instruction.Read.code,
            tlvs = byteArrayOf(),
            p1 = 0x01,
            p2 = 0x02,
        )
        val data = apdu.apduData
        assertThat(data[2]).isEqualTo(0x01.toByte())
        assertThat(data[3]).isEqualTo(0x02.toByte())
    }

    @Test
    fun `CommandApdu custom CLA`() {
        val apdu = CommandApdu(
            ins = Instruction.Read.code,
            tlvs = byteArrayOf(),
            p1 = 0,
            p2 = 0,
            cla = 0x80,
        )
        assertThat(apdu.apduData[0]).isEqualTo(0x80.toByte())
    }

    @Test
    fun `CommandApdu serialize is idempotent`() {
        val payload = byteArrayOf(0x01, 0x02)
        val apdu = CommandApdu(Instruction.Read, payload)
        assertThat(apdu.serialize()).isEqualTo(apdu.serialize())
        assertThat(apdu.serialize()).isEqualTo(apdu.apduData)
    }

    @Test
    fun `CommandApdu toString contains instruction name`() {
        val apdu = CommandApdu(Instruction.Sign, byteArrayOf())
        assertThat(apdu.toString()).contains("Sign")
    }

    @Test
    fun `CommandApdu toString contains byte count`() {
        val apdu = CommandApdu(Instruction.Read, byteArrayOf(0x01))
        assertThat(apdu.toString()).contains("bytes")
    }

    @Test
    fun `CommandApdu encrypt with null key returns same instance`() {
        val apdu = CommandApdu(Instruction.Read, byteArrayOf(0x01))
        val result = apdu.encrypt(
            encryptionMode = com.tangem.common.encryption.EncryptionMode.None,
            encryptionKey = null,
        )
        assertThat(result.apduData).isEqualTo(apdu.apduData)
    }

    @Test
    fun `CommandApdu encrypt skips if p1 is not None`() {
        val apdu = CommandApdu(
            ins = Instruction.Read.code,
            tlvs = byteArrayOf(0x01),
            p1 = 0x01, // non-zero p1 = already encrypted
            p2 = 0,
        )
        val key = ByteArray(32) { 0x01 }
        val result = apdu.encrypt(
            encryptionMode = com.tangem.common.encryption.EncryptionMode.Fast,
            encryptionKey = key,
        )
        assertThat(result.apduData).isEqualTo(apdu.apduData)
    }

    // endregion

    // region ResponseApdu

    @Test
    fun `ResponseApdu parses status word from last 2 bytes`() {
        // 0x90, 0x00 = ProcessCompleted
        val data = byteArrayOf(0x90.toByte(), 0x00.toByte())
        val response = ResponseApdu.fromRawBytes(data)
        assertThat(response.sw).isEqualTo(0x9000)
        assertThat(response.statusWord).isEqualTo(StatusWord.ProcessCompleted)
    }

    @Test
    fun `ResponseApdu with payload and status word`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x90.toByte(), 0x00.toByte())
        val response = ResponseApdu.fromRawBytes(data)
        assertThat(response.sw).isEqualTo(0x9000)
        assertThat(response.statusWord).isEqualTo(StatusWord.ProcessCompleted)
    }

    @Test
    fun `ResponseApdu getTlvData returns null when only status word`() {
        val data = byteArrayOf(0x90.toByte(), 0x00.toByte())
        val response = ResponseApdu.fromRawBytes(data)
        assertThat(response.getTlvData()).isEmpty()
    }

    @Test
    fun `ResponseApdu getTlvData parses TLV payload`() {
        // TLV: tag=0x01(CardId), len=2, value=0xABCD + SW=0x9000
        val data = byteArrayOf(
            0x01, 0x02, 0xAB.toByte(), 0xCD.toByte(),
            0x90.toByte(), 0x00.toByte(),
        )
        val response = ResponseApdu.fromRawBytes(data)
        val tlvs = response.getTlvData()
        assertThat(tlvs).isNotNull()
        assertThat(tlvs).hasSize(1)
        assertThat(tlvs!![0].tag).isEqualTo(TlvTag.CardId)
        assertThat(tlvs[0].value).isEqualTo(byteArrayOf(0xAB.toByte(), 0xCD.toByte()))
    }

    @Test
    fun `ResponseApdu InvalidParams status word`() {
        val data = byteArrayOf(0x6A.toByte(), 0x86.toByte())
        val response = ResponseApdu.fromRawBytes(data)
        assertThat(response.statusWord).isEqualTo(StatusWord.InvalidParams)
    }

    @Test
    fun `ResponseApdu NeedEncryption status word`() {
        val data = byteArrayOf(0x69.toByte(), 0x82.toByte())
        val response = ResponseApdu.fromRawBytes(data)
        assertThat(response.statusWord).isEqualTo(StatusWord.NeedEncryption)
    }

    @Test
    fun `ResponseApdu unknown status word`() {
        val data = byteArrayOf(0x12.toByte(), 0x34.toByte())
        val response = ResponseApdu.fromRawBytes(data)
        assertThat(response.statusWord).isEqualTo(StatusWord.Unknown)
    }

    @Test
    fun `ResponseApdu decrypt with null key returns same data`() {
        val data = byteArrayOf(0x01, 0x02, 0x90.toByte(), 0x00.toByte())
        val response = ResponseApdu.fromRawBytes(data)
        val decrypted = response.decrypt(
            encryptionMode = com.tangem.common.encryption.EncryptionMode.None,
            encryptionKey = null,
        )
        assertThat(decrypted.sw).isEqualTo(response.sw)
        assertThat(decrypted.getTlvData()?.size).isEqualTo(response.getTlvData()?.size)
    }

    @Test
    fun `ResponseApdu toString contains status word`() {
        val data = byteArrayOf(0x90.toByte(), 0x00.toByte())
        val response = ResponseApdu.fromRawBytes(data)
        val str = response.toString()
        assertThat(str).contains("ProcessCompleted")
        assertThat(str).contains("bytes")
    }

    // endregion

    // region Instruction

    @Test
    fun `Instruction byCode returns correct instruction`() {
        assertThat(Instruction.byCode(0xF2)).isEqualTo(Instruction.Read)
        assertThat(Instruction.byCode(0xFB)).isEqualTo(Instruction.Sign)
        assertThat(Instruction.byCode(0xF1)).isEqualTo(Instruction.Personalize)
        assertThat(Instruction.byCode(0xF8)).isEqualTo(Instruction.CreateWallet)
    }

    @Test
    fun `Instruction byCode returns Unknown for invalid code`() {
        assertThat(Instruction.byCode(0x99)).isEqualTo(Instruction.Unknown)
    }

    @Test
    fun `Instruction codes are unique`() {
        val codes = Instruction.values().map { it.code }
        assertThat(codes).containsNoDuplicates()
    }

    // endregion

    // region StatusWord

    @Test
    fun `StatusWord byCode returns correct status`() {
        assertThat(StatusWord.byCode(0x9000)).isEqualTo(StatusWord.ProcessCompleted)
        assertThat(StatusWord.byCode(0x6A86)).isEqualTo(StatusWord.InvalidParams)
        assertThat(StatusWord.byCode(0x6985)).isEqualTo(StatusWord.InvalidState)
        assertThat(StatusWord.byCode(0x6D00)).isEqualTo(StatusWord.InsNotSupported)
        assertThat(StatusWord.byCode(0x6982)).isEqualTo(StatusWord.NeedEncryption)
        assertThat(StatusWord.byCode(0x6A82)).isEqualTo(StatusWord.FileNotFound)
        assertThat(StatusWord.byCode(0x6A88)).isEqualTo(StatusWord.WalletNotFound)
    }

    @Test
    fun `StatusWord byCode returns Unknown for unrecognized code`() {
        assertThat(StatusWord.byCode(0x1234)).isEqualTo(StatusWord.Unknown)
    }

    @Test
    fun `StatusWord pin change codes`() {
        assertThat(StatusWord.byCode(0x9001)).isEqualTo(StatusWord.Pin1Changed)
        assertThat(StatusWord.byCode(0x9002)).isEqualTo(StatusWord.Pin2Changed)
        assertThat(StatusWord.byCode(0x9003)).isEqualTo(StatusWord.Pins12Changed)
        assertThat(StatusWord.byCode(0x9007)).isEqualTo(StatusWord.Pins123Changed)
    }

    // endregion

    // region StatusWord.toTangemSdkError

    @Test
    fun `toTangemSdkError returns null for ProcessCompleted`() {
        assertThat(StatusWord.ProcessCompleted.toTangemSdkError()).isNull()
    }

    @Test
    fun `toTangemSdkError returns null for pin change statuses`() {
        assertThat(StatusWord.Pin1Changed.toTangemSdkError()).isNull()
        assertThat(StatusWord.Pin2Changed.toTangemSdkError()).isNull()
        assertThat(StatusWord.Pins12Changed.toTangemSdkError()).isNull()
        assertThat(StatusWord.Pin3Changed.toTangemSdkError()).isNull()
        assertThat(StatusWord.Pins13Changed.toTangemSdkError()).isNull()
        assertThat(StatusWord.Pins23Changed.toTangemSdkError()).isNull()
        assertThat(StatusWord.Pins123Changed.toTangemSdkError()).isNull()
    }

    @Test
    fun `toTangemSdkError returns null for NeedPause`() {
        assertThat(StatusWord.NeedPause.toTangemSdkError()).isNull()
    }

    @Test
    fun `toTangemSdkError returns correct error for error statuses`() {
        assertThat(StatusWord.InvalidParams.toTangemSdkError())
            .isInstanceOf(TangemSdkError.InvalidParams::class.java)
        assertThat(StatusWord.ErrorProcessingCommand.toTangemSdkError())
            .isInstanceOf(TangemSdkError.ErrorProcessingCommand::class.java)
        assertThat(StatusWord.InvalidState.toTangemSdkError())
            .isInstanceOf(TangemSdkError.InvalidState::class.java)
        assertThat(StatusWord.InsNotSupported.toTangemSdkError())
            .isInstanceOf(TangemSdkError.InsNotSupported::class.java)
        assertThat(StatusWord.NeedEncryption.toTangemSdkError())
            .isInstanceOf(TangemSdkError.NeedEncryption::class.java)
        assertThat(StatusWord.FileNotFound.toTangemSdkError())
            .isInstanceOf(TangemSdkError.FileNotFound::class.java)
        assertThat(StatusWord.WalletNotFound.toTangemSdkError())
            .isInstanceOf(TangemSdkError.WalletNotFound::class.java)
        assertThat(StatusWord.InvalidAccessCode.toTangemSdkError())
            .isInstanceOf(TangemSdkError.AccessCodeRequired::class.java)
        assertThat(StatusWord.InvalidPasscode.toTangemSdkError())
            .isInstanceOf(TangemSdkError.PasscodeRequired::class.java)
        assertThat(StatusWord.WalletAlreadyExists.toTangemSdkError())
            .isInstanceOf(TangemSdkError.WalletAlreadyCreated::class.java)
        assertThat(StatusWord.NeedReset.toTangemSdkError())
            .isInstanceOf(TangemSdkError.NeedReset::class.java)
        assertThat(StatusWord.AccessDenied.toTangemSdkError())
            .isInstanceOf(TangemSdkError.AccessDenied::class.java)
    }

    @Test
    fun `toTangemSdkError returns null for Unknown`() {
        assertThat(StatusWord.Unknown.toTangemSdkError()).isNull()
    }

    // endregion

    // region Integration: CommandApdu + ResponseApdu roundtrip

    @Test
    fun `build CommandApdu with TlvBuilder payload`() {
        val tlvBuilder = TlvBuilder()
        tlvBuilder.append(TlvTag.CardId, "CB22000000027374")
        tlvBuilder.append(TlvTag.WalletIndex, 0)

        val apdu = CommandApdu(Instruction.Read, tlvBuilder.serialize())
        val data = apdu.apduData

        // Verify header
        assertThat(data[0]).isEqualTo(CommandApdu.ISO_CLA.toByte())
        assertThat(data[1]).isEqualTo(Instruction.Read.code.toByte())

        // Verify it has data after header
        assertThat(data.size).isGreaterThan(4)
    }

    @Test
    fun `ResponseApdu with multiple TLVs parses correctly`() {
        // Build TLV payload manually:
        // CardId(0x01) len=2 value=AABB + IsActivated(0x3A) len=1 value=0x01 + SW 0x9000
        val data = byteArrayOf(
            0x01, 0x02, 0xAA.toByte(), 0xBB.toByte(),
            0x3A, 0x01, 0x01,
            0x90.toByte(), 0x00.toByte(),
        )
        val response = ResponseApdu.fromRawBytes(data)
        assertThat(response.statusWord).isEqualTo(StatusWord.ProcessCompleted)

        val tlvs = response.getTlvData()
        assertThat(tlvs).isNotNull()
        assertThat(tlvs).hasSize(2)
        assertThat(tlvs!![0].tag).isEqualTo(TlvTag.CardId)
        assertThat(tlvs[1].tag).isEqualTo(TlvTag.IsActivated)
    }

    // endregion
}
package com.tangem.commands.read

import com.tangem.Log
import com.tangem.SessionEnvironment
import com.tangem.TangemSdkError
import com.tangem.commands.Command
import com.tangem.commands.CommandResponse
import com.tangem.commands.wallet.CardWallet
import com.tangem.commands.wallet.WalletIndex
import com.tangem.commands.wallet.addTlvData
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag

/**
[REDACTED_AUTHOR]
 */
class WalletResponse(
    val cid: String,
    val wallet: CardWallet
) : CommandResponse

class ReadWalletCommand(
    private val walletIndex: WalletIndex
) : Command<WalletResponse>() {

    override fun needPreflightRead(): Boolean = false

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = TlvBuilder()
        tlvBuilder.append(TlvTag.Pin, environment.pin1?.value)
        tlvBuilder.append(TlvTag.InteractionMode, ReadMode.ReadWallet)
        tlvBuilder.append(TlvTag.CardId, environment.card?.cardId)
        tlvBuilder.append(TlvTag.TerminalPublicKey, environment.terminalKeys?.publicKey)
        walletIndex.addTlvData(tlvBuilder)
        return CommandApdu(Instruction.Read, tlvBuilder.serialize())
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): WalletResponse {
        val tlvData = apdu.getTlvData() ?: throw TangemSdkError.DeserializeApduFailed()

        val decoder = TlvDecoder(tlvData)
        val wallet = CardWallet.deserialize(decoder)
        val cid = decoder.decode<String>(TlvTag.CardId)
        Log.debug { "Read wallet at index: $walletIndex: $wallet" }
        return WalletResponse(cid, wallet)
    }
}
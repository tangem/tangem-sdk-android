package com.tangem.commands.read

import com.tangem.CardSession
import com.tangem.SessionEnvironment
import com.tangem.TangemSdkError
import com.tangem.commands.Command
import com.tangem.commands.CommandResponse
import com.tangem.commands.wallet.CardWallet
import com.tangem.commands.wallet.WalletIndex
import com.tangem.commands.wallet.addTlvData
import com.tangem.common.CompletionResult
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.extensions.guard
import com.tangem.common.tlv.Tlv
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag

/**
[REDACTED_AUTHOR]
 */
class WalletListResponse(
    val cid: String,
    val wallets: List<CardWallet>
) : CommandResponse

class ReadWalletListCommand : Command<WalletListResponse>() {

    private var walletIndex: WalletIndex? = null
    private val tempWalletList = mutableListOf<CardWallet>()

    override fun needPreflightRead(): Boolean = false

    override fun run(session: CardSession, callback: (result: CompletionResult<WalletListResponse>) -> Unit) {
        val card = session.environment.card.guard {
            callback(CompletionResult.Failure(TangemSdkError.CardError()))
            return
        }

        transceive(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    tempWalletList.addAll(result.data.wallets)
                    val loadedWalletsCount = tempWalletList.size
                    if (loadedWalletsCount == 0 && result.data.wallets.isEmpty()) {
                        callback(CompletionResult.Failure(TangemSdkError.CardWithMaxZeroWallets()))
                        return@transceive
                    }
                    if (loadedWalletsCount != card.walletsCount) {
                        walletIndex = WalletIndex.Index(loadedWalletsCount)
                        run(session, callback)
                        return@transceive
                    }
                    callback(CompletionResult.Success(WalletListResponse(result.data.cid, tempWalletList)))
                }
                is CompletionResult.Failure -> {
                    callback(CompletionResult.Failure(result.error))
                }
            }
        }
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = TlvBuilder()
        tlvBuilder.append(TlvTag.Pin, environment.pin1?.value)
        tlvBuilder.append(TlvTag.InteractionMode, ReadMode.ReadWalletList)
        tlvBuilder.append(TlvTag.TerminalPublicKey, environment.terminalKeys?.publicKey)
        walletIndex?.addTlvData(tlvBuilder)
        return CommandApdu(Instruction.Read, tlvBuilder.serialize())
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): WalletListResponse {
        val tlvData = apdu.getTlvData() ?: throw TangemSdkError.DeserializeApduFailed()

        val decoder = TlvDecoder(tlvData)
        val cardWalletsData: List<ByteArray> = decoder.decodeArray(TlvTag.CardWallet)
        if (cardWalletsData.isEmpty()) throw TangemSdkError.DeserializeApduFailed()

        val walletDecoders = cardWalletsData.mapNotNull { Tlv.deserialize(it) }.map { TlvDecoder(it) }
        val wallets = walletDecoders.map { CardWallet.deserialize(it) }
        val cid = decoder.decode<String>(TlvTag.CardId)

        return WalletListResponse(cid, wallets)
    }
}
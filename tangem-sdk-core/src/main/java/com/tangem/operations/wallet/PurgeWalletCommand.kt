package com.tangem.operations.wallet

import com.tangem.common.CompletionResult
import com.tangem.common.SuccessResponse
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.Card
import com.tangem.common.core.CardSession
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.guard
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag
import com.tangem.operations.Command

/**
 * This command deletes all wallet data and its private and public keys
 * @property walletPublicKey: Public key of the wallet to delete
 */
class PurgeWalletCommand(
    private val walletIndex: Int?,
    private val walletPublicKey: ByteArray?
) : Command<SuccessResponse>() {

    override fun requiresPasscode(): Boolean = true

    override fun performPreCheck(card: Card): TangemSdkError? {
        if( walletPublicKey!=null ) {
            val wallet = card.wallet(walletPublicKey)

            return when {
                wallet == null -> TangemSdkError.WalletNotFound()
                wallet.settings.isPermanent -> TangemSdkError.PurgeWalletProhibited()
                ( walletIndex!=null && (walletIndex>card.wallets.size || wallet!=card.wallets[walletIndex])) ->
                {
                    TangemSdkError.WalletNotFound()
                }
                else -> null
            }
        }else if(walletIndex!=null){
            return if(walletIndex>card.wallets.size )
            {
                TangemSdkError.WalletNotFound()
            }else null
        }else{
            return TangemSdkError.WalletNotFound()
        }
    }

    override fun run(session: CardSession, callback: CompletionCallback<SuccessResponse>) {
        val card = session.environment.card.guard {
            callback(CompletionResult.Failure(TangemSdkError.CardError()))
            return
        }

        super.run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    val walletIndex = walletIndex?:card.wallet(walletPublicKey!!)?.index ?: throw TangemSdkError.WalletNotFound()
                    session.environment.card = card.removeWallet(walletIndex)
                    callback(CompletionResult.Success(result.data))
                }
                is CompletionResult.Failure -> callback(result)
            }
        }
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val walletIndex = walletIndex?:environment.card?.wallet(walletPublicKey!!)?.index ?: throw TangemSdkError.WalletNotFound()

        val tlvBuilder = TlvBuilder()
        tlvBuilder.append(TlvTag.Pin, environment.accessCode.value)
        tlvBuilder.append(TlvTag.Pin2, environment.passcode.value)
        tlvBuilder.append(TlvTag.CardId, environment.card?.cardId)
        tlvBuilder.append(TlvTag.WalletIndex, walletIndex)

        return CommandApdu(Instruction.PurgeWallet, tlvBuilder.serialize())
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): SuccessResponse {
        val tlvData = apdu.getTlvData() ?: throw TangemSdkError.DeserializeApduFailed()

        val decoder = TlvDecoder(tlvData)
        return SuccessResponse(decoder.decode(TlvTag.CardId))
    }
}
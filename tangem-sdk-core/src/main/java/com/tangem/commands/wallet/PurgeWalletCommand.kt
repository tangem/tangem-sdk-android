package com.tangem.commands.wallet

import com.tangem.CardSession
import com.tangem.SessionEnvironment
import com.tangem.TangemError
import com.tangem.TangemSdkError
import com.tangem.commands.Command
import com.tangem.commands.CommandResponse
import com.tangem.commands.common.card.Card
import com.tangem.commands.common.card.CardStatus
import com.tangem.commands.common.card.masks.Settings
import com.tangem.common.CompletionResult
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.extensions.guard
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag
import com.tangem.tasks.PreflightReadSettings

class PurgeWalletResponse(
    /**
     * CID, Unique Tangem card ID number.
     */
    val cardId: String,
    /**
     * Current status of the card [1 - Empty, 2 - Loaded, 3- Purged]
     */
    val status: CardStatus,
    /**
     * Index of purged wallet
     */
    val walletIndex: WalletIndex
) : CommandResponse

/**
 * This command deletes all wallet data. If Is_Reusable flag is enabled during personalization,

 * If Is_Reusable flag is disabled, the card switches to ‘Purged’ state.
 * ‘Purged’ state is final, it makes the card useless.
 * @property cardId CID, Unique Tangem card ID number.
 */
class PurgeWalletCommand(
    private val walletIndex: WalletIndex
) : Command<PurgeWalletResponse>() {

    override val requiresPin2 = true

    override fun preflightReadSettings(): PreflightReadSettings = PreflightReadSettings.ReadWallet(walletIndex)

    override fun run(session: CardSession, callback: (result: CompletionResult<PurgeWalletResponse>) -> Unit) {
        super.run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    val card = session.environment.card.guard {
                        callback(CompletionResult.Failure(TangemSdkError.CardError()))
                        return@run
                    }

                    card.status = CardStatus.Empty
                    val wallet = card.wallet(result.data.walletIndex)
                    if (wallet == null) {
                        callback(CompletionResult.Failure(TangemSdkError.WalletIndexNotCorrect()))
                    } else {
                        card.updateWallet(CardWallet(wallet.index, WalletStatus.Empty))
                        session.environment.card = card
                        callback(CompletionResult.Success(result.data))
                    }
                }
                is CompletionResult.Failure -> {
                    callback(CompletionResult.Failure(result.error))
                }
            }
        }
    }

    override fun performPreCheck(card: Card): TangemSdkError? {
        if (card.status == CardStatus.NotPersonalized) {
            return TangemSdkError.NotPersonalized()
        }
        if (card.isActivated) {
            return TangemSdkError.NotActivated()
        }

        val wallet = card.wallet(walletIndex) ?: return TangemSdkError.WalletNotFound()
        when (wallet.status) {
            WalletStatus.Empty -> return TangemSdkError.WalletIsNotCreated()
            WalletStatus.Purged -> return TangemSdkError.WalletIsPurged()
        }

        if (card.settingsMask?.contains(Settings.ProhibitPurgeWallet) == true) {
            return TangemSdkError.PurgeWalletProhibited()
        }

        return null
    }

    override fun mapError(card: Card?, error: TangemError): TangemError {
        if (error is TangemSdkError.InvalidParams) {
            return TangemSdkError.Pin2OrCvcRequired()
        }
        return error
    }


    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = TlvBuilder()
        tlvBuilder.append(TlvTag.CardId, environment.card?.cardId)
        tlvBuilder.append(TlvTag.Pin, environment.pin1?.value)
        tlvBuilder.append(TlvTag.Pin2, environment.pin2?.value)
        walletIndex.addTlvData(tlvBuilder)
        return CommandApdu(Instruction.PurgeWallet, tlvBuilder.serialize())
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): PurgeWalletResponse {
        val tlvData = apdu.getTlvData() ?: throw TangemSdkError.DeserializeApduFailed()

        val decoder = TlvDecoder(tlvData)
        var index = walletIndex
        decoder.decodeOptional<Int?>(TlvTag.WalletIndex)?.let { index = WalletIndex.Index(it) }

        return PurgeWalletResponse(
            cardId = decoder.decode(TlvTag.CardId),
            status = decoder.decode(TlvTag.Status),
            walletIndex = index
        )
    }
}
package com.tangem.operations.wallet

import com.tangem.common.CompletionResult
import com.tangem.common.SuccessResponse
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.CardSession
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.core.TangemSdkError
import com.tangem.common.tlv.TlvTag
import com.tangem.operations.Command

/**
 * This command deletes all wallet data and its private and public keys
 * @property walletIndex: Index of the wallet to delete
 */
class PurgeWalletCommand(
    private val walletIndex: Int,
) : Command<SuccessResponse>() {

    override fun requiresPasscode(): Boolean = true

    override fun performPreCheck(card: Card): TangemSdkError? {
        val wallet = card.wallets.firstOrNull { it.index == walletIndex }
            ?: return TangemSdkError.WalletNotFound()

        if (wallet.settings.isPermanent) {
            return TangemSdkError.PurgeWalletProhibited()
        }

        return null
    }

    override fun run(session: CardSession, callback: CompletionCallback<SuccessResponse>) {
        super.run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    session.environment.card?.wallets?.let { wallets ->
                        session.environment.card = session.environment.card?.setWallets(
                            wallets.filter { it.index != walletIndex },
                        )
                    }
                    callback(CompletionResult.Success(result.data))
                }
                is CompletionResult.Failure -> callback(result)
            }
        }
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val card = environment.card ?: throw TangemSdkError.MissingPreflightRead()

        val tlvBuilder = createTlvBuilder(environment.legacyMode)
        tlvBuilder.append(TlvTag.WalletIndex, walletIndex)
        if (shouldAddPin(environment.accessCode, card.firmwareVersion)) {
            tlvBuilder.append(TlvTag.Pin, environment.accessCode.value)
        }
        if (shouldAddPin(environment.passcode, card.firmwareVersion)) {
            tlvBuilder.append(TlvTag.Pin2, environment.accessCode.value)
        }
        if (card.firmwareVersion < FirmwareVersion.v8) {
            tlvBuilder.append(TlvTag.CardId, environment.card?.cardId)
        }

        return CommandApdu(Instruction.PurgeWallet, tlvBuilder.serialize())
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): SuccessResponse {
        val decoder = createTlvDecoder(environment, apdu)
        return SuccessResponse(decoder.decode(TlvTag.CardId))
    }
}
package com.tangem.commands.common.card

import com.tangem.FirmwareConstraints
import com.tangem.TangemSdkError
import com.tangem.commands.wallet.CardWallet
import com.tangem.commands.wallet.WalletStatus
import com.tangem.common.TangemSdkConstants
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.tlv.Tlv
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag

class CardDeserializer() {
    companion object {
        fun deserialize(apdu: ResponseApdu): Card {
            val tlvData = apdu.getTlvData() ?: throw TangemSdkError.DeserializeApduFailed()

            val decoder = TlvDecoder(tlvData)
            val version: String? = decoder.decodeOptional(TlvTag.Firmware)
            val firmwareVersion = if (version == null) FirmwareVersion.zero else FirmwareVersion(version)

            val card = Card(
                cardId = decoder.decodeOptional(TlvTag.CardId) ?: "",
                manufacturerName = decoder.decodeOptional(TlvTag.ManufactureId) ?: "",
                firmwareVersion = firmwareVersion,
                status = decoder.decodeOptional(TlvTag.Status),
                cardPublicKey = decoder.decodeOptional(TlvTag.CardPublicKey),
                defaultCurve = decoder.decodeOptional(TlvTag.CurveId),
                settingsMask = decoder.decodeOptional(TlvTag.SettingsMask),
                issuerPublicKey = decoder.decodeOptional(TlvTag.IssuerDataPublicKey),
                signingMethods = decoder.decodeOptional(TlvTag.SigningMethod),
                pauseBeforePin2 = decoder.decodeOptional(TlvTag.PauseBeforePin2),
                walletsCount = decoder.decodeOptional(TlvTag.WalletsCount),
                walletIndex = decoder.decodeOptional(TlvTag.WalletIndex),
                health = decoder.decodeOptional(TlvTag.Health),
                isActivated = decoder.decode(TlvTag.IsActivated),
                activationSeed = decoder.decodeOptional(TlvTag.ActivationSeed),
                paymentFlowVersion = decoder.decodeOptional(TlvTag.PaymentFlowVersion),
                userCounter = decoder.decodeOptional(TlvTag.UserCounter),
                userProtectedCounter = decoder.decodeOptional(TlvTag.UserProtectedCounter),
                terminalIsLinked = decoder.decode(TlvTag.TerminalIsLinked),
                cardData = deserializeCardData(tlvData)
            )

            if (firmwareVersion < FirmwareConstraints.AvailabilityVersions.walletData && card.status != null) {
                val wallet = CardWallet(
                    TangemSdkConstants.oldCardDefaultWalletIndex,
                    WalletStatus.from(card.status!!),
                    card.defaultCurve,
                    card.settingsMask,
                    decoder.decodeOptional(TlvTag.WalletPublicKey),
                    decoder.decodeOptional(TlvTag.WalletSignedHashes),
                    decoder.decodeOptional(TlvTag.WalletRemainingSignatures)
                )
                card.wallets = mutableListOf(wallet)
            }
            return card
        }

        private fun deserializeCardData(tlvData: List<Tlv>): CardData? {
            val cardDataTlvs = tlvData.find { it.tag == TlvTag.CardData }?.let {
                Tlv.deserialize(it.value)
            }
            if (cardDataTlvs.isNullOrEmpty()) return null

            val decoder = TlvDecoder(cardDataTlvs)
            return CardData(
                batchId = decoder.decodeOptional(TlvTag.Batch),
                manufactureDateTime = decoder.decodeOptional(TlvTag.ManufactureDateTime),
                issuerName = decoder.decodeOptional(TlvTag.IssuerId),
                blockchainName = decoder.decodeOptional(TlvTag.BlockchainId),
                manufacturerSignature = decoder.decodeOptional(TlvTag.ManufacturerSignature),
                productMask = decoder.decodeOptional(TlvTag.ProductMask),

                tokenSymbol = decoder.decodeOptional(TlvTag.TokenSymbol),
                tokenContractAddress = decoder.decodeOptional(TlvTag.TokenContractAddress),
                tokenDecimal = decoder.decodeOptional(TlvTag.TokenDecimal)
            )
        }
    }
}
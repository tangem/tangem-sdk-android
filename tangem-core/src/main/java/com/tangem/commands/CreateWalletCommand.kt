package com.tangem.commands

import com.tangem.FirmwareConstraints
import com.tangem.SessionEnvironment
import com.tangem.TangemError
import com.tangem.TangemSdkError
import com.tangem.commands.common.card.Card
import com.tangem.commands.common.card.CardStatus
import com.tangem.commands.common.card.FirmwareVersion
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag

class CreateWalletResponse(
    /**
     * CID, Unique Tangem card ID number.
     */
    val cardId: String,
    /**
     * Current status of the card [1 - Empty, 2 - Loaded, 3- Purged]
     */
    val status: CardStatus,
    /**
     * Wallet index on card.
     * Note: Available only for cards with COS v.4.0 and higher
     */
    val walletIndex: Int?,
    /**

     */
    val walletPublicKey: ByteArray
) : CommandResponse

/**
 * This command will create a new wallet on the card having ‘Empty’ state.
 * A key pair WalletPublicKey / WalletPrivateKey is generated and securely stored in the card.
 * App will need to obtain Wallet_PublicKey from the response of [CreateWalletCommand] or [ReadCommand]
 * and then transform it into an address of corresponding blockchain wallet
 * according to a specific blockchain algorithm.
 * WalletPrivateKey is never revealed by the card and will be used by [SignCommand] and [CheckWalletCommand].
 * RemainingSignature is set to MaxSignatures.
 *
 * @property cardId CID, Unique Tangem card ID number.
 */
class CreateWalletCommand(
    private val walletConfig: WalletConfig?,
    private val walletIndexValue: Int?
) : Command<CreateWalletResponse>(), WalletSelectable {

    override val requiresPin2 = true

    override var walletIndex: WalletIndex? = null
        get() = if (walletIndexValue == null) null else WalletIndex.Index(walletIndexValue)

    override fun performPreCheck(card: Card): TangemSdkError? {
        if (card.isActivated) {
            return TangemSdkError.NotActivated()
        }

        fun getStatusError(status: CardStatus?): TangemSdkError? {
            return when (status) {
                CardStatus.Empty -> null
                CardStatus.NotPersonalized -> TangemSdkError.NotPersonalized()
                CardStatus.Loaded -> TangemSdkError.AlreadyCreated()
                CardStatus.Purged -> TangemSdkError.CardIsPurged()
                null -> TangemSdkError.CardError()
            }
        }

        val isWalletDataAvailable = card.firmwareVersion >= FirmwareConstraints.AvailabilityVersions.walletData

        card.status?.let { status ->
            getStatusError(status)?.let { error ->
                if (isWalletDataAvailable) {
                    if (walletIndexValue == card.walletIndex) return error
                } else {
                    return error
                }
            }
        }
        walletIndexValue?.let { walletIndex ->
            if (isWalletDataAvailable && walletIndex >= card.walletsCount ?: 1) {
                return TangemSdkError.WalletIndexExceedsMaxValue()
            }
        }

        return null
    }

    override fun mapError(card: Card?, error: TangemError): TangemError {
        if (error is TangemSdkError.InvalidParams) {
            val card = card ?: return TangemSdkError.Pin2OrCvcRequired()

            card.walletsCount?.let { walletsCount ->
                walletIndexValue?.let { walletIndex ->
                    if (walletIndex >= walletsCount) return TangemSdkError.WalletIndexExceedsMaxValue()
                }
            }

            if (card.firmwareVersion >= FirmwareConstraints.AvailabilityVersions.pin2IsDefault && card.isPin2Default == true) {
                return TangemSdkError.AlreadyCreated()
            }
        }
        return error
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = TlvBuilder()
        tlvBuilder.append(TlvTag.Pin, environment.pin1?.value)
        tlvBuilder.append(TlvTag.CardId, environment.card?.cardId)
        tlvBuilder.append(TlvTag.Pin2, environment.pin2?.value)
        tlvBuilder.append(TlvTag.Cvc, environment.cvc)
        walletIndex?.addTlvData(tlvBuilder)

        val firmwareVersion = environment.card?.firmwareVersion ?: FirmwareVersion.zero
        if (firmwareVersion >= FirmwareConstraints.AvailabilityVersions.walletData) {
            walletConfig?.let { config ->
                serializeWalletData(walletConfig.walletData)?.let { serializedWalletData ->
                    tlvBuilder.append(TlvTag.SettingsMask, config.getSettingsMask())
                    tlvBuilder.append(TlvTag.CurveId, config.curveId)
                    tlvBuilder.append(TlvTag.WalletData, serializedWalletData)
                }
            }
        }
        return CommandApdu(Instruction.CreateWallet, tlvBuilder.serialize())
    }

    private fun serializeWalletData(walletData: WalletData): ByteArray? {
        val walletDataItemList = listOf<Any?>(walletData.blockchainName, walletData.tokenSymbol,
            walletData.tokenContractAddress, walletData.tokenDecimal)
        if (walletDataItemList.filterNotNull().size != walletDataItemList.size) return null

        val tlvBuilder = TlvBuilder()
        tlvBuilder.append(TlvTag.BlockchainId, walletData.blockchainName)
        tlvBuilder.append(TlvTag.TokenSymbol, walletData.tokenSymbol)
        tlvBuilder.append(TlvTag.TokenContractAddress, walletData.tokenContractAddress)
        tlvBuilder.append(TlvTag.TokenDecimal, walletData.tokenDecimal)
        return tlvBuilder.serialize()
    }

    override fun deserialize(
        environment: SessionEnvironment,
        apdu: ResponseApdu
    ): CreateWalletResponse {
        val tlvData = apdu.getTlvData()
            ?: throw TangemSdkError.DeserializeApduFailed()

        val decoder = TlvDecoder(tlvData)
        return CreateWalletResponse(
            cardId = decoder.decode(TlvTag.CardId),
            status = decoder.decode(TlvTag.Status),
            walletPublicKey = decoder.decode(TlvTag.WalletPublicKey),
            walletIndex = decoder.decodeOptional(TlvTag.WalletsIndex)
        )
    }
}
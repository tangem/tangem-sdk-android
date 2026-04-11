package com.tangem.operations.read

import com.squareup.moshi.JsonClass
import com.tangem.common.CompletionResult
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.Card
import com.tangem.common.card.CardWallet
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.*
import com.tangem.common.deserialization.WalletDeserializer
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvTag
import com.tangem.operations.Command
import com.tangem.operations.CommandResponse
import com.tangem.operations.PreflightReadMode

@JsonClass(generateAdapter = true)
class ReadWalletsListResponse(
    val cardId: String,
    val wallets: List<CardWallet>,
    val backupHash: ByteArray? = null,
) : CommandResponse

/**
 * Read all wallets on card.
 */
class ReadWalletsListCommand : Command<ReadWalletsListResponse>() {

    private val loadedWallets = mutableListOf<CardWallet>()
    private var receivedWalletsCount: Int = 0
    private var backupHash: ByteArray? = null

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.ReadCardOnly

    override fun performPreCheck(card: Card): TangemError? {
        if (card.firmwareVersion < FirmwareVersion.MultiWalletAvailable) {
            return TangemSdkError.NotSupportedFirmwareVersion()
        }

        return null
    }

    override fun run(session: CardSession, callback: CompletionCallback<ReadWalletsListResponse>) {
        transceive(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    loadedWallets.addAll(result.data.wallets)
                    if (result.data.backupHash != null) backupHash = result.data.backupHash
                    if (receivedWalletsCount == 0 && result.data.wallets.isEmpty()) {
                        callback(CompletionResult.Failure(TangemSdkError.CardWithMaxZeroWallets()))
                        return@transceive
                    }

                    if (receivedWalletsCount != session.environment.card?.settings?.maxWalletsCount) {
                        run(session, callback)
                        return@transceive
                    }

                    loadedWallets.sortBy { it.index }
                    session.environment.card = session.environment.card?.setWallets(loadedWallets)
                    callback(
                        CompletionResult.Success(
                            ReadWalletsListResponse(result.data.cardId, loadedWallets, backupHash),
                        ),
                    )
                }
                is CompletionResult.Failure -> callback(result)
            }
        }
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val card = environment.card ?: throw TangemSdkError.MissingPreflightRead()

        val tlvBuilder = createTlvBuilder(environment.legacyMode)
        tlvBuilder.append(TlvTag.InteractionMode, ReadMode.WalletsList)
        if (shouldAddPin(environment.accessCode, card.firmwareVersion)) {
            tlvBuilder.append(TlvTag.Pin, environment.accessCode.value)
        }
        if (card.firmwareVersion < FirmwareVersion.v8) {
            tlvBuilder.append(TlvTag.CardId, environment.card?.cardId)
        }
        if (receivedWalletsCount > 0) tlvBuilder.append(TlvTag.WalletIndex, receivedWalletsCount)

        return CommandApdu(Instruction.Read, tlvBuilder.serialize())
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): ReadWalletsListResponse {
        val card = environment.card ?: throw TangemSdkError.UnknownError()
        val decoder = createTlvDecoder(environment, apdu)
        val deserializedData = WalletDeserializer(card.settings.isPermanentWallet)
            .deserializeWallets(decoder)
        receivedWalletsCount += deserializedData.second

        val backupHash: ByteArray? = decoder.decodeOptional(TlvTag.BackupHash)
        return ReadWalletsListResponse(decoder.decode(TlvTag.CardId), deserializedData.first, backupHash)
    }
}
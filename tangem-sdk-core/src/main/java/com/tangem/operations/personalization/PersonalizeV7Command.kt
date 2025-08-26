package com.tangem.operations.personalization

import com.tangem.Log
import com.tangem.common.CompletionResult
import com.tangem.common.UserCodeType
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.Card
import com.tangem.common.card.EncryptionMode
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.CardSession
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.core.TangemSdkError
import com.tangem.common.deserialization.CardDeserializer
import com.tangem.common.extensions.calculateSha256
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvTag
import com.tangem.crypto.sign
import com.tangem.operations.Command
import com.tangem.operations.PreflightReadMode
import com.tangem.operations.PreflightReadTask
import com.tangem.operations.personalization.entities.Acquirer
import com.tangem.operations.personalization.entities.CardConfig
import com.tangem.operations.personalization.entities.CardConfigV7
import com.tangem.operations.personalization.entities.CardData
import com.tangem.operations.personalization.entities.Issuer
import com.tangem.operations.personalization.entities.Manufacturer
import com.tangem.operations.personalization.entities.createCardId
import com.tangem.operations.personalization.entities.createSettingsMask
import com.tangem.operations.read.ReadCommand

/**
 * Command available on SDK cards only
 *
 * Personalization is an initialization procedure, required before starting using a card.
 * During this procedure a card setting is set up.
 * During this procedure all data exchange is encrypted.
 * @property config is a configuration file with all the card settings that are written on the card
 * during personalization.
 * @property issuer Issuer is a third-party team or company wishing to use Tangem cards.
 * (non-EMV) POS terminal infrastructure and transaction processing back-end.
 *
 * Warning: Command available only for cards with COS 7.0 and higher. Legacy devices not supported.
 */
class PersonalizeV7Command(
    private val config: CardConfigV7,
    private val issuer: Issuer,
) : Command<Card>() {

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.None

    override fun run(session: CardSession, callback: CompletionCallback<Card>) {
        Log.command(this)
        // We have to run preflight read ourselves to catch the notPersonalized error
        val read = PreflightReadTask(PreflightReadMode.ReadCardOnly)
        read.run(session) { result ->
            when (result) {
                is CompletionResult.Success -> callback(CompletionResult.Failure(TangemSdkError.AlreadyPersonalized()))
                is CompletionResult.Failure -> {
                    if (result.error is TangemSdkError.NotPersonalized) {
                        if( result.error.fwVersion==null || result.error.fwVersion<FirmwareVersion.v7)
                        {
                            callback(CompletionResult.Failure(TangemSdkError.NotSupportedFirmwareVersion()))
                        }else {
                            runPersonalize(session, callback)
                        }
                    } else {
                        callback(CompletionResult.Failure(result.error))
                    }
                }
            }
        }
    }

    private fun runPersonalize(session: CardSession, callback: CompletionCallback<Card>) {
        session.environment.encryptionMode = EncryptionMode.None
        session.environment.encryptionKey = null
        super.run(session) { result ->
            callback(result)
        }
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val nonce = byteArrayOf(0x7E) + ByteArray(11) { it.toByte() }
        return CommandApdu(Instruction.Personalize, serializePersonalizationData(config)).encryptCcm(
            devPersonalizationKey,
            nonce,
            includeNonce = true
        )
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): Card {
        val decryptedApdu = apdu.decryptCcm(devPersonalizationKey)
        val decoder = CardDeserializer.getDecoder(decryptedApdu)
        val cardDataDecoder = CardDeserializer.getCardDataDecoder(decoder.tlvList)

        val isAccessCodeSet = config.pin != UserCodeType.AccessCode.defaultValue
        return CardDeserializer.deserialize(isAccessCodeSet, decoder, cardDataDecoder, true)
    }

    private fun serializePersonalizationData(config: CardConfigV7): ByteArray {
        val cardId = config.createCardId() ?: throw TangemSdkError.SerializeCommandError()

        val cardData = config.cardData.createPersonalizationCardData()
        val createWallet = config.createWallet != 0

        val tlvBuilder = TlvBuilder()
        tlvBuilder.append(TlvTag.CardId, cardId)
        tlvBuilder.append(TlvTag.WalletsCount, config.walletsCount)
        tlvBuilder.append(TlvTag.SettingsMask, config.createSettingsMask())
        tlvBuilder.append(TlvTag.PauseBeforePin2, config.securityDelay.div(other = 10))
        if( config.useNDEF ) {
            tlvBuilder.append(TlvTag.NdefData, serializeNdef(config))
        }
        tlvBuilder.append(TlvTag.NewPin, config.pinSha256())
        tlvBuilder.append(TlvTag.CreateWalletAtPersonalize, createWallet)
        if( createWallet ) {
            tlvBuilder.append(TlvTag.CurveId, config.curveID)
            tlvBuilder.append(TlvTag.SigningMethod, config.signingMethod)
        }
        tlvBuilder.append(TlvTag.IssuerPublicKey, issuer.dataKeyPair.publicKey)
        tlvBuilder.append(TlvTag.IssuerTransactionPublicKey, issuer.transactionKeyPair.publicKey)
        tlvBuilder.append(TlvTag.CardData, serializeCardData(cardData, cardId))

        return tlvBuilder.serialize()
    }

    private fun serializeNdef(config: CardConfigV7): ByteArray {
        return if (config.ndefRecords.isEmpty()) {
            ByteArray(0)
        } else {
            NdefEncoder(config.ndefRecords, false).encode()
        }
    }

    private fun serializeCardData(cardData: CardData, cardId: String): ByteArray {
        val tlvBuilder = TlvBuilder()
        tlvBuilder.append(TlvTag.BatchId, cardData.batchId)
        tlvBuilder.append(TlvTag.ManufactureDateTime, cardData.manufactureDateTime)
        tlvBuilder.append(TlvTag.IssuerName, issuer.id)

        return tlvBuilder.serialize()
    }

    companion object {
        val devPersonalizationKey = "1234".calculateSha256().copyOf(32)
    }
}
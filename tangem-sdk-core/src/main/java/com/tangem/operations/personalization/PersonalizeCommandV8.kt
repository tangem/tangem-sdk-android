package com.tangem.operations.personalization

import com.tangem.Log
import com.tangem.common.CompletionResult
import com.tangem.common.UserCodeType
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.CardSession
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.core.TangemSdkError
import com.tangem.common.deserialization.CardDeserializer
import com.tangem.common.encryption.EncryptionMode
import com.tangem.common.extensions.calculateSha256
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvTag
import com.tangem.crypto.encryptAesCcm
import com.tangem.operations.Command
import com.tangem.operations.PreflightReadMode
import com.tangem.operations.PreflightReadTask
import com.tangem.operations.personalization.config.CardConfigV8
import com.tangem.operations.personalization.config.CardData
import com.tangem.operations.personalization.config.CardIdBuilder
import com.tangem.operations.personalization.config.CardSettingsMaskBuilderV8
import com.tangem.operations.personalization.config.Issuer
import com.tangem.operations.personalization.config.Manufacturer

/**
 * Command available on SDK cards only
 *
 * Personalization is an initialization procedure, required before starting using a card.
 * During this procedure a card setting is set up.
 * During this procedure all data exchange is encrypted.
 * @property config is a configuration file with all the card settings that are written on the card
 * during personalization.
 * @property issuer Issuer is a third-party team or company wishing to use Tangem cards.
 * @property manufacturer Tangem Card Manufacturer.
 *
 * Warning: Command available only for cards with COS v8 and higher.
 */
class PersonalizeCommandV8(
    private val config: CardConfigV8,
    private val issuer: Issuer,
    private val manufacturer: Manufacturer,
) : Command<Card>() {

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.None

    override fun run(session: CardSession, callback: CompletionCallback<Card>) {
        Log.command(this)
        // We have to run preflight read ourselves to catch the notPersonalized error
        val read = PreflightReadTask(
            readMode = PreflightReadMode.ReadCardOnly,
            secureStorage = session.environment.secureStorage,
        )
        read.run(session) { result ->
            when (result) {
                is CompletionResult.Success -> callback(CompletionResult.Failure(TangemSdkError.AlreadyPersonalized()))
                is CompletionResult.Failure -> {
                    val error = result.error
                    if (error is TangemSdkError.NotPersonalized) {
                        if (error.firmware < FirmwareVersion.v8) {
                            callback(CompletionResult.Failure(TangemSdkError.NotSupportedFirmwareVersion()))
                            return@run
                        }
                        runPersonalize(session, callback)
                    } else {
                        callback(result)
                    }
                }
            }
        }
    }

    private fun runPersonalize(session: CardSession, callback: CompletionCallback<Card>) {
        val encryptionMode = session.environment.encryptionMode
        val encryptionKey = session.environment.encryptionKey
        session.environment.encryptionMode = EncryptionMode.None
        session.environment.encryptionKey = null
        super.run(session) { result ->
            session.environment.encryptionMode = encryptionMode
            session.environment.encryptionKey = encryptionKey
            callback(result)
        }
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvData = serializePersonalizationData(config, environment)
        return encryptApdu(tlvData)
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): Card {
        val decryptedApdu = apdu.decryptCcm(devPersonalizationKey, NONCE_LENGTH)
        val decoder = CardDeserializer.getDecoder(decryptedApdu)
        val cardDataDecoder = CardDeserializer.getCardDataDecoder(decoder.tlvList)

        val isAccessCodeSet = config.pin != UserCodeType.AccessCode.defaultValue
        return CardDeserializer.deserialize(isAccessCodeSet, decoder, cardDataDecoder, true)
    }

    private fun encryptApdu(tlvData: ByteArray): CommandApdu {
        val p1 = 0x00
        val nonce = ByteArray(NONCE_LENGTH).also {
            it[0] = 0x7E.toByte()
            for (i in 1 until NONCE_LENGTH) {
                it[i] = (i - 1).toByte()
            }
        }
        val associatedData = byteArrayOf(
            CommandApdu.ISO_CLA.toByte(),
            Instruction.Personalize.code.toByte(),
            p1.toByte(),
            0x00.toByte(), // p2
        )

        val encryptedPayload = tlvData.encryptAesCcm(
            key = devPersonalizationKey,
            nonce = nonce,
            associatedData = associatedData,
        )

        val encryptedData = nonce + encryptedPayload

        Log.apdu { "C-APDU encrypted with CCM" }

        return CommandApdu(
            Instruction.Personalize.code,
            encryptedData,
            p1,
            0x00, // p2
        )
    }

    @Suppress("LongMethod")
    private fun serializePersonalizationData(config: CardConfigV8, environment: SessionEnvironment): ByteArray {
        val cardId = CardIdBuilder.createCardId(config) ?: throw TangemSdkError.SerializeCommandError()

        val cardData = config.cardData.createPersonalizationCardData()
        val createWallet = config.createWallet != 0

        val tlvBuilder = createTlvBuilder(environment.legacyMode)
        tlvBuilder.append(TlvTag.CardId, cardId)
        tlvBuilder.append(TlvTag.SettingsMask, CardSettingsMaskBuilderV8.createSettingsMask(config))
        tlvBuilder.append(TlvTag.PauseBeforePin2, config.securityDelay / 10)
        tlvBuilder.append(TlvTag.CreateWalletAtPersonalize, createWallet)
        tlvBuilder.append(TlvTag.NewPin, config.pinSha256())
        tlvBuilder.append(TlvTag.IssuerPublicKey, issuer.dataKeyPair.publicKey)
        tlvBuilder.append(TlvTag.IssuerTransactionPublicKey, issuer.transactionKeyPair.publicKey)
        tlvBuilder.append(TlvTag.CardData, serializeCardData(cardData, environment))

        if (createWallet) {
            tlvBuilder.append(TlvTag.CurveId, config.curveID)
            tlvBuilder.append(TlvTag.SigningMethod, config.signingMethod)
        }

        if (config.walletsCount != null) {
            tlvBuilder.append(TlvTag.WalletsCount, config.walletsCount)
        }

        if (config.useNDEF) {
            tlvBuilder.append(TlvTag.NdefData, serializeNdef(config))
        }

        return tlvBuilder.serialize()
    }

    private fun serializeNdef(config: CardConfigV8): ByteArray {
        return if (config.ndefRecords.isEmpty()) {
            ByteArray(0)
        } else {
            NdefEncoder(config.ndefRecords, false).encode()
        }
    }

    private fun serializeCardData(cardData: CardData, environment: SessionEnvironment): ByteArray {
        val tlvBuilder = createTlvBuilder(environment.legacyMode)
        tlvBuilder.append(TlvTag.BatchId, cardData.batchId)
        tlvBuilder.append(TlvTag.ManufactureDateTime, cardData.manufactureDateTime)
        tlvBuilder.append(TlvTag.IssuerName, issuer.id)
        return tlvBuilder.serialize()
    }

    companion object {
        private const val NONCE_LENGTH = 12
        private val devPersonalizationKey = "1234".calculateSha256().copyOf(32)
    }
}
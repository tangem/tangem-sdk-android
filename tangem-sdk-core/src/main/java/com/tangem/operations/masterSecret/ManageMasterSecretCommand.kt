package com.tangem.operations.masterSecret

import com.tangem.common.CompletionResult
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.Card
import com.tangem.common.card.EllipticCurve
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.CardSession
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.core.TangemError
import com.tangem.common.core.TangemSdkError
import com.tangem.common.core.toTangemSdkError
import com.tangem.common.deserialization.MasterSecretDeserializer
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag
import com.tangem.crypto.hdWallet.bip32.ExtendedPrivateKey
import com.tangem.operations.Command
import com.tangem.operations.read.ReadMasterSecretResponse

/**
 * This command will create/purge a master secret on the card having.
 *  @param privateKey: A private key to import.
 */
class ManageMasterSecretCommand @Throws constructor(
    private val mode: ManageMasterSecretMode,
    private val privateKey: ExtendedPrivateKey? = null,
) : Command<ReadMasterSecretResponse>() {

    override fun performPreCheck(card: Card): TangemSdkError? {
        if (card.firmwareVersion < FirmwareVersion.MasterSecretAvailable) {
            return TangemSdkError.UnsupportedWalletConfig()
        }

        if (privateKey != null) {
            if (!card.settings.isKeysImportAllowed) {
                return TangemSdkError.KeysImportDisabled()
            }
            try {
                val extendedKey = privateKey.makePublicKey(EllipticCurve.Secp256k1)
            } catch (e: TangemSdkError.UnsupportedCurve) {
                // ignore exception
            } catch (e: Exception) {
                return e.toTangemSdkError()
            }
        }

        return null
    }

    override fun run(session: CardSession, callback: CompletionCallback<ReadMasterSecretResponse>) {
        super.run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    session.environment.card = session.environment.card?.setMasterSecret(result.data.masterSecret)
                    callback(CompletionResult.Success(result.data))
                }
                is CompletionResult.Failure -> callback(result)
            }
        }
    }

    override fun mapError(card: Card?, error: TangemError): TangemError {
        if (error is TangemSdkError.InvalidState) {
            return TangemSdkError.AlreadyCreated()
        }

        return error
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = TlvBuilder()

        tlvBuilder.append(TlvTag.InteractionMode, mode)

        if (privateKey != null) {
            tlvBuilder.append(TlvTag.WalletPrivateKey, privateKey.privateKey)
            tlvBuilder.append(TlvTag.WalletHDChain, privateKey.chainCode)
        }

        return CommandApdu(Instruction.ManageMasterSecret, tlvBuilder.serialize())
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): ReadMasterSecretResponse {
        val tlvData = apdu.getTlvData() ?: throw TangemSdkError.DeserializeApduFailed()

        val decoder = TlvDecoder(tlvData)
        val masterSecret = MasterSecretDeserializer.deserializeMasterSecret(decoder)

        return ReadMasterSecretResponse(masterSecret)
    }
}
package com.tangem.operations.read

import com.squareup.moshi.JsonClass
import com.tangem.common.CompletionResult
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.MasterSecret
import com.tangem.common.core.CardSession
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.deserialization.CardDeserializer
import com.tangem.common.deserialization.MasterSecretDeserializer
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvTag
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.operations.Command
import com.tangem.operations.CommandResponse
import com.tangem.operations.PreflightReadMode

@JsonClass(generateAdapter = true)
data class ReadMasterSecretResponse(
    val masterSecret: MasterSecret?,
) : CommandResponse

/**
 * This command receives from the Tangem Card
 */
class ReadMasterSecretCommand(private val derivationPath: DerivationPath? = null) : Command<ReadMasterSecretResponse>() {

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.None

    override fun run(session: CardSession, callback: CompletionCallback<ReadMasterSecretResponse>) {
        super.run(session) {
            when (it) {
                is CompletionResult.Success -> {
                    callback(it)
                }
                is CompletionResult.Failure -> callback(CompletionResult.Failure(it.error))
            }
        }
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val tlvBuilder = TlvBuilder()
        tlvBuilder.append(TlvTag.InteractionMode, ReadMode.MasterSecret)
        tlvBuilder.append(TlvTag.WalletHDPath, derivationPath)
        return CommandApdu(Instruction.Read, tlvBuilder.serialize())
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): ReadMasterSecretResponse {
        val decoder = CardDeserializer.getDecoder(apdu)
        val masterSecret = MasterSecretDeserializer.deserializeMasterSecret(decoder)
        return ReadMasterSecretResponse(masterSecret)
    }
}
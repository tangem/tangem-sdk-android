package com.tangem.common.deserialization

import com.tangem.common.card.MasterSecret
import com.tangem.common.core.TangemSdkError
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag

/**
 * Deserialize master secrets
 */
internal object MasterSecretDeserializer{

    internal fun deserializeMasterSecret(decoder: TlvDecoder): MasterSecret? {
        val status: MasterSecret.Status? = decoder.decode(TlvTag.Status)
        return if (status?.isAvailable == true) {
            deserialize(decoder, status)
        } else {
            null
        }
    }

    private fun deserialize(decoder: TlvDecoder, status: MasterSecret.Status): MasterSecret {

        return MasterSecret(
            publicKey = decoder.decodeOptional(TlvTag.WalletPublicKey),
            chainCode = decoder.decodeOptional(TlvTag.WalletHDChain),
            isImported = status.isImported,
            hasBackup = status == MasterSecret.Status.BackedUp,
        )
    }
}
package com.tangem.common.deserialization

import com.tangem.common.card.WalletData
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag

/**
[REDACTED_AUTHOR]
 */
object WalletDataDeserializer {
    fun deserialize(decoder: TlvDecoder): WalletData? {
        val blockchain: String = decoder.decodeOptional(TlvTag.BlockchainName) ?: return null

        return WalletData(blockchain, deserializeToken(decoder))
    }

    private fun deserializeToken(decoder: TlvDecoder): WalletData.Token? {
        val tokenName: String? = decoder.decodeOptional(TlvTag.TokenName)
        val tokenSymbol: String = decoder.decodeOptional(TlvTag.TokenSymbol)
            ?: return null
        val tokenContractAddress: String = decoder.decodeOptional(TlvTag.TokenContractAddress)
            ?: return null
        val tokenDecimals: Int = decoder.decodeOptional(TlvTag.TokenDecimal)
            ?: return null

        return WalletData.Token(
            name = tokenName ?: tokenSymbol,
            symbol = tokenSymbol,
            contractAddress = tokenContractAddress,
            decimals = tokenDecimals
        )
    }
}
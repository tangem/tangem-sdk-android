package com.tangem.commands

import com.tangem.commands.common.card.EllipticCurve
import com.tangem.commands.common.card.masks.SigningMethod
import com.tangem.commands.common.card.masks.WalletSettingsMask
import com.tangem.commands.common.card.masks.WalletSettingsMaskBuilder

class WalletData(
    val blockchainName: String?,
    val tokenSymbol: String? = null,
    val tokenContractAddress: String? = null,
    val tokenDecimal: Int? = null
)

class WalletConfig(
    val isReusable: Boolean,
    val prohibitPurgeWallet: Boolean,
    val curveId: EllipticCurve,
    val signingMethods: SigningMethod,
    val walletData: WalletData) {

    fun getSettingsMask(): WalletSettingsMask {
        val builder = WalletSettingsMaskBuilder()
        if (isReusable) {
            builder.add(WalletSettingsMask.isReusable)
        }
        if (prohibitPurgeWallet) {
            builder.add(WalletSettingsMask.prohibitPurgeWallet)
        }
        return builder.build()
    }
}
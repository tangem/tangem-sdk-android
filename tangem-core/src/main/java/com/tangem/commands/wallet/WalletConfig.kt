package com.tangem.commands.wallet

import com.tangem.commands.common.card.EllipticCurve
import com.tangem.commands.common.card.masks.SigningMethod
import com.tangem.commands.common.card.masks.WalletSetting
import com.tangem.commands.common.card.masks.WalletSettingsMask
import com.tangem.commands.common.card.masks.WalletSettingsMaskBuilder

class WalletConfig(
    val isReusable: Boolean,
    val prohibitPurgeWallet: Boolean,
    val curveId: EllipticCurve,
    val signingMethods: SigningMethod,
) {

    fun getSettingsMask(): WalletSettingsMask {
        val builder = WalletSettingsMaskBuilder()
        if (isReusable) {
            builder.add(WalletSetting.IsReusable)
        }
        if (prohibitPurgeWallet) {
            builder.add(WalletSetting.ProhibitPurgeWallet)
        }
        return builder.build()
    }
}
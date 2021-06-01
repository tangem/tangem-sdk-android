package com.tangem.commands.wallet

import com.squareup.moshi.JsonClass
import com.tangem.commands.common.card.EllipticCurve
import com.tangem.commands.common.card.masks.SigningMethod
import com.tangem.commands.common.card.masks.WalletSetting
import com.tangem.commands.common.card.masks.WalletSettingsMask
import com.tangem.commands.common.card.masks.WalletSettingsMaskBuilder

@JsonClass(generateAdapter = true)
class WalletConfig(
    val isReusable: Boolean?,
    val prohibitPurgeWallet: Boolean?,
    val curveId: EllipticCurve?,
    val signingMethods: SigningMethod?,
) {

    fun getSettingsMask(): WalletSettingsMask? {
        if (isReusable == null && prohibitPurgeWallet == null) return null

        val builder = WalletSettingsMaskBuilder()
        if (isReusable == true) {
            builder.add(WalletSetting.IsReusable)
        }
        if (prohibitPurgeWallet == true) {
            builder.add(WalletSetting.ProhibitPurgeWallet)
        }
        return builder.build()
    }
}
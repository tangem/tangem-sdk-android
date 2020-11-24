package com.tangem.commands.common.card.masks

class WalletSettingsMask(var rawValue: Int) {
    companion object {
        val isReusable = WalletSettingsMask(0x0001)
        val prohibitPurgeWallet = WalletSettingsMask(0x0004)
    }
}

class WalletSettingsMaskBuilder {
    private var settingsMaskValue = 0

    fun add(settings: WalletSettingsMask) {
        settingsMaskValue = settingsMaskValue or settings.rawValue
    }

    fun build(): WalletSettingsMask = WalletSettingsMask(settingsMaskValue)
}
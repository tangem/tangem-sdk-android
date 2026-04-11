package com.tangem.operations.personalization.config

import com.tangem.common.Mask
import com.tangem.common.card.Card

internal object CardSettingsMaskBuilderV8 {

    fun createSettingsMask(config: CardConfigV8): Card.SettingsMask {
        val builder = MaskBuilder()

        builder.add(Card.SettingsMask.Code.IsReusable)
        builder.addIf(config.allowSetPIN, Card.SettingsMask.Code.AllowSetPIN1)
        builder.addIf(config.useNDEF, Card.SettingsMask.Code.UseNDEF)
        builder.addIf(config.prohibitDefaultPIN, Card.SettingsMask.Code.ProhibitDefaultPIN1)
        builder.addIf(config.useActivation, Card.SettingsMask.Code.UseActivation)
        builder.addIf(config.useBlock, Card.SettingsMask.Code.UseBlock)
        builder.addIf(config.prohibitPurgeWallet, Card.SettingsMask.Code.PermanentWallet)
        builder.addIf(config.disableFiles, Card.SettingsMask.Code.DisableFiles)
        builder.addIf(config.allowHDWallets, Card.SettingsMask.Code.AllowHDWallets)
        builder.addIf(config.allowBackup, Card.SettingsMask.Code.AllowBackup)
        builder.addIf(config.allowKeysImport, Card.SettingsMask.Code.AllowKeysImport)
        builder.addIf(config.requireBackup, Card.SettingsMask.Code.RequireBackup)

        return builder.build()
    }

    private fun MaskBuilder.addIf(condition: Boolean?, maskCode: Mask.Code) {
        if (condition == true) add(maskCode)
    }
}
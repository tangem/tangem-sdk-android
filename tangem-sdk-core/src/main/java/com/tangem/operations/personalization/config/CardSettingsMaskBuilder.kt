package com.tangem.operations.personalization.config

import com.tangem.common.Mask
import com.tangem.common.card.Card

internal object CardSettingsMaskBuilder {

    fun createSettingsMask(config: CardConfig): Card.SettingsMask {
        val builder = MaskBuilder()

        val isReusable = config.isReusable ?: true
        builder.addIf(isReusable, Card.SettingsMask.Code.IsReusable)
        builder.addIf(config.allowSetPIN1, Card.SettingsMask.Code.AllowSetPIN1)
        builder.addIf(config.allowSetPIN2, Card.SettingsMask.Code.AllowSetPIN2)
        builder.addIf(config.useCvc, Card.SettingsMask.Code.UseCvc)
        builder.addIf(config.useOneCommandAtTime, Card.SettingsMask.Code.UseOneCommandAtTime)
        builder.addIf(config.useNDEF, Card.SettingsMask.Code.UseNDEF)
        builder.addIf(config.useDynamicNDEF, Card.SettingsMask.Code.UseDynamicNDEF)
        builder.addIf(config.disablePrecomputedNDEF, Card.SettingsMask.Code.DisablePrecomputedNDEF)
        builder.addIf(config.allowUnencrypted, Card.SettingsMask.Code.AllowUnencrypted)
        builder.addIf(config.allowFastEncryption, Card.SettingsMask.Code.AllowFastEncryption)
        builder.addIf(config.prohibitDefaultPIN1, Card.SettingsMask.Code.ProhibitDefaultPIN1)
        builder.addIf(config.useActivation, Card.SettingsMask.Code.UseActivation)
        builder.addIf(config.useBlock, Card.SettingsMask.Code.UseBlock)
        builder.addIf(config.smartSecurityDelay, Card.SettingsMask.Code.SmartSecurityDelay)
        builder.addIf(config.protectIssuerDataAgainstReplay, Card.SettingsMask.Code.ProtectIssuerDataAgainstReplay)
        builder.addIf(config.prohibitPurgeWallet, Card.SettingsMask.Code.PermanentWallet)
        builder.addIf(config.allowSelectBlockchain, Card.SettingsMask.Code.AllowSelectBlockchain)
        builder.addIf(
            config.skipCheckPIN2CVCIfValidatedByIssuer,
            Card.SettingsMask.Code.SkipCheckPIN2CVCIfValidatedByIssuer,
        )
        builder.addIf(
            config.skipSecurityDelayIfValidatedByIssuer,
            Card.SettingsMask.Code.SkipSecurityDelayIfValidatedByIssuer,
        )
        builder.addIf(
            condition = config.skipSecurityDelayIfValidatedByLinkedTerminal,
            maskCode = Card.SettingsMask.Code.SkipSecurityDelayIfValidatedByLinkedTerminal,
        )
        builder.addIf(config.restrictOverwriteIssuerDataEx, Card.SettingsMask.Code.RestrictOverwriteIssuerExtraData)
        builder.addIf(config.disableIssuerData, Card.SettingsMask.Code.DisableIssuerData)
        builder.addIf(config.disableUserData, Card.SettingsMask.Code.DisableUserData)
        builder.addIf(config.disableFiles, Card.SettingsMask.Code.DisableFiles)
        builder.addIf(config.allowHDWallets, Card.SettingsMask.Code.AllowHDWallets)
        builder.addIf(config.allowBackup, Card.SettingsMask.Code.AllowBackup)
        builder.addIf(config.allowKeysImport, Card.SettingsMask.Code.AllowKeysImport)
        builder.addIf(isReusable, Card.SettingsMask.Code.IsReusable)

        return builder.build()
    }

    private fun MaskBuilder.addIf(condition: Boolean?, maskCode: Mask.Code) {
        if (condition == true) add(maskCode)
    }
}
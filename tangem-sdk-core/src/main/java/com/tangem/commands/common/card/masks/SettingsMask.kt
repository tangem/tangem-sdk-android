package com.tangem.commands.common.card.masks

import com.tangem.common.MASK_DELIMITER

/**
 * Stores and maps Tangem card settings.
 *
 * @property rawValue Card settings in a form of flags,
 * while flags definitions and possible values are in [Settings].
 */
data class SettingsMask(val rawValue: Int) {
    fun contains(settings: Settings): Boolean = (rawValue and settings.code) != 0

    override fun toString(): String {
        return Settings.values().filter { contains(it) }.joinToString(MASK_DELIMITER)
    }

    companion object {
        fun fromString(strMask: String): SettingsMask {
            return SettingsMaskBuilder().apply {
                strMask.split(MASK_DELIMITER).forEach {
                    try {
                        add(Settings.valueOf(it))
                    } catch (ex: IllegalArgumentException) {
                        ex.printStackTrace()
                    }
                }
            }.build()
        }
    }
}

enum class Settings(val code: Int) {
    IsReusable(0x0001),
    UseActivation(0x0002),
    ProhibitPurgeWallet(0x0004),
    UseBlock(0x0008),

    AllowSetPIN1(0x0010),
    AllowSetPIN2(0x0020),
    UseCvc(0x0040),
    ProhibitDefaultPIN1(0x0080),

    UseOneCommandAtTime(0x0100),
    UseNDEF(0x0200),
    UseDynamicNDEF(0x0400),
    SmartSecurityDelay(0x0800),

    AllowUnencrypted(0x1000),
    AllowFastEncryption(0x2000),

    ProtectIssuerDataAgainstReplay(0x4000),
    RestrictOverwriteIssuerExtraData(0x00100000),

    AllowSelectBlockchain(0x8000),

    DisablePrecomputedNDEF(0x00010000),

    SkipSecurityDelayIfValidatedByLinkedTerminal(0x00080000),
    SkipCheckPIN2CVCIfValidatedByIssuer(0x00040000),
    SkipSecurityDelayIfValidatedByIssuer(0x00020000),

    RequireTermTxSignature(0x01000000),
    RequireTermCertSignature(0x02000000),
    CheckPIN3OnCard(0x04000000)
}

class SettingsMaskBuilder() {

    private var settingsMaskValue = 0

    fun add(settings: Settings) {
        settingsMaskValue = settingsMaskValue or settings.code
    }

    fun build() = SettingsMask(settingsMaskValue)

}
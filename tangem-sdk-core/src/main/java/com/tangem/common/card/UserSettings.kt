package com.tangem.common.card

import com.tangem.common.BaseMask
import com.tangem.common.Mask
import com.tangem.operations.personalization.config.MaskBuilder

data class UserSettings(
    /**
     * Is allowed to recover user codes
     */
    val isUserCodeRecoveryAllowed: Boolean,

    /**
     * Is required Pin to open session for v8+ cards
     */
    val isPINRequired: Boolean,

    /**
     * Is read NDEF feature disabled
     */
    val isNDEFDisabled: Boolean,
) {
    val mask: UserSettingsMask
        get() {
            val builder = MaskBuilder()
            if (!isUserCodeRecoveryAllowed) builder.add(UserSettingsMask.Code.ForbidResetPIN)
            if (isPINRequired) builder.add(UserSettingsMask.Code.ForbidResetPIN)
            if (isNDEFDisabled) builder.add(UserSettingsMask.Code.ForbidResetPIN)
            return builder.build()
        }

    internal constructor(
        mask: UserSettingsMask,
    ) : this(
        isUserCodeRecoveryAllowed = !mask.contains(UserSettingsMask.Code.ForbidResetPIN),
        isPINRequired = mask.contains(UserSettingsMask.Code.RequirePin),
        isNDEFDisabled = mask.contains(UserSettingsMask.Code.DisableNFC),
    )
}

class UserSettingsMask(override var rawValue: Int) : BaseMask() {

    override val values: List<Code> = Code.values().toList()

    enum class Code(override val value: Int) : Mask.Code {
        ForbidResetPIN(value = 0x00000001),
        RequirePin(value = 0x00000002),
        DisableNFC(value = 0x00000010),
    }
}
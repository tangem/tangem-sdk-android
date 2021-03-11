package com.tangem

import com.tangem.commands.common.card.FirmwareVersion

/**
[REDACTED_AUTHOR]
 */
class FirmwareConstraints {
    object AvailabilityVersions {
        val walletData = FirmwareVersion(4, 0)
        val pin2IsDefault = FirmwareVersion(4, 0)
        val files = FirmwareVersion(3, 29)
        val pauseBeforePin2 = FirmwareVersion(1, 21)
    }

    object DeprecationVersions {
        val walletRemainingSignatures = FirmwareVersion(4, 0)
    }
}
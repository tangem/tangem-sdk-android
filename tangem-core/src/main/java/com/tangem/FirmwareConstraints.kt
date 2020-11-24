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
    }

    object DeprecationVersions {
        val walletRemainingSignatures = FirmwareVersion(4, 0)
    }
}
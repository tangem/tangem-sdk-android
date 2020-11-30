package com.tangem.common.extensions

import com.tangem.commands.common.card.Card
import com.tangem.commands.common.card.FirmwareType

fun Card.getType(): FirmwareType {
    return firmwareVersion.type ?: FirmwareType.Sprecial
}
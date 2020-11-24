package com.tangem.common.extensions

import com.tangem.commands.common.card.Card
import com.tangem.commands.common.card.CardType
import com.tangem.commands.common.card.FirmwareType
import com.tangem.commands.common.card.FirmwareVersion

fun Card.getType(): CardType {
    return getFirmwareVersion().type ?: FirmwareType.Sprecial
}

fun Card.getFirmwareVersion(): FirmwareVersion {
    val version = this.firmwareVersion ?: return FirmwareVersion.zero
    return FirmwareVersion(version)
//    return this.firmwareVersion?.let {firmwareVersion ->
//        val matchResult = """(\d+.\d+)([\w\s]+)""".toRegex().find(firmwareVersion)
//        matchResult?.destructured?.component1()?.toDoubleOrNull()
//    }
}
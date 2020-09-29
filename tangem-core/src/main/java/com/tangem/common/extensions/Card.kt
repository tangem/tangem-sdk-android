package com.tangem.common.extensions

import com.tangem.commands.Card

fun Card.getType(): CardType {
    val firmware = this.firmwareVersion ?: return CardType.Unknown
    return when {
        firmware.endsWith("d SDK") -> {
            CardType.Sdk
        }
        firmware.endsWith("r") -> {
            CardType.Release
        }
        else -> {
            CardType.Unknown
        }
    }
}

enum class CardType {
    Sdk, Release, Unknown
}

fun Card.getFirmwareNumber(): Double? {
    return this.firmwareVersion?.let {firmwareVersion ->
        val matchResult = """(\d+.\d+)([\w\s]+)""".toRegex().find(firmwareVersion)
        matchResult?.destructured?.component1()?.toDoubleOrNull()
    }
}
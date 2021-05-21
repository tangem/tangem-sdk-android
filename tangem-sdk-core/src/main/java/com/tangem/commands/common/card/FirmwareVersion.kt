package com.tangem.commands.common.card

enum class FirmwareType(val rawValue: String?) {
    Sdk("d SDK"),
    Release("r"),
    Sprecial(null);

    companion object {
        fun from(type: String): FirmwareType? = values().find { it.rawValue == type }
    }
}

typealias CardType = FirmwareType


/**
 * Holds information about card firmware version included version saved on card `version`,
 * splitted to `major`, `minor` and `hotFix` and `FirmwareType`
 */
class FirmwareVersion : Comparable<FirmwareVersion> {
    var major: Int = 0
        private set

    var minor: Int = 0
        private set

    var hotFix: Int = 0
        private set

    var type: FirmwareType? = null
        private set

    val versionDouble: Double
        get() = "$major.$minor".toDouble()

    val version: String

    constructor(version: String) {
        this.version = version

        val versionCleaned = version.removeSuffix("\u0000")

        val cardTypeStr = versionCleaned.trim { "0123456789.".contains(it) }
        val result = versionCleaned.replace(cardTypeStr, "")
        val splitted = result.split(".").toMutableList()

        splitted.removeFirstOrNull()?.let { this.major = it.toInt() }
        splitted.removeFirstOrNull()?.let { this.minor = it.toInt() }
        splitted.removeFirstOrNull()?.let { this.hotFix = it.toInt() }

        type = FirmwareType.from(cardTypeStr)
    }

    constructor(major: Int, minor: Int, hotFix: Int = 0, type: FirmwareType = FirmwareType.Sdk) {
        this.major = major
        this.minor = minor
        this.hotFix = hotFix
        this.type = type

        this.version = StringBuilder()
            .append("$major.$minor")
            .append(if (hotFix != 0) ".$hotFix" else "")
            .append(type.rawValue)
            .toString()
    }

    override fun compareTo(other: FirmwareVersion): Int = when {
        major != other.major -> major.compareTo(other.major)
        minor != other.minor -> minor.compareTo(other.minor)
        else -> hotFix.compareTo(other.hotFix)
    }

    companion object {
        val zero = FirmwareVersion(0, 0)
        val max = FirmwareVersion(Int.MAX_VALUE, 0)
    }
}
package com.tangem.sdk.ui

/**
 * Wrapper for NFC location data that supports both predefined device locations
 * and dynamically calculated antenna positions from Android NfcAntennaInfo API
 */
sealed class NfcLocationData {

    /** Predefined NFC location from device database */
    data class Predefined(val location: NfcLocation) : NfcLocationData()

    /** Dynamically calculated NFC antenna position from Android API */
    data class Dynamic(val position: NfcAntennaPosition) : NfcLocationData()

    val orientation: Int
        get() = when (this) {
            is Predefined -> location.orientation
            is Dynamic -> position.orientation
        }

    val x: Float
        get() = when (this) {
            is Predefined -> location.x
            is Dynamic -> position.x
        }

    val y: Float
        get() = when (this) {
            is Predefined -> location.y
            is Dynamic -> position.y
        }

    val z: Int
        get() = when (this) {
            is Predefined -> location.z
            is Dynamic -> position.z
        }

    val isHorizontal: Boolean get() = orientation == 0

    val isOnTheBack: Boolean get() = z == 0
}
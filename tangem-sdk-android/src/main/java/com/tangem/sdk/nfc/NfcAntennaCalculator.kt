package com.tangem.sdk.nfc

import android.nfc.NfcAdapter
import android.os.Build
import androidx.annotation.RequiresApi
import com.tangem.Log
import com.tangem.sdk.ui.NfcAntennaPosition

/**
 * Calculator for NFC antenna position using Android's NfcAntennaInfo API
 * Available from Android API 34 (Android 14)
 */
internal class NfcAntennaCalculator(
    private val nfcAdapter: NfcAdapter?,
) {

    /**
     * Attempts to calculate NFC antenna position from device's NFC hardware info
     * Returns null if:
     * - Android version < 34 (API not available)
     * - NFC adapter is not available
     * - NFC antenna info is not available
     * - Calculation fails for any reason
     */
    fun calculatePosition(): NfcAntennaPosition? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.debug { "NFC antenna position calculation not available - requires API 34+" }
            return null
        }

        return try {
            calculateFromApi34()
        } catch (e: Exception) {
            Log.error { "Failed to calculate NFC antenna position: ${e.message}" }
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun calculateFromApi34(): NfcAntennaPosition? {
        val adapter = nfcAdapter ?: return null
        val antennaInfo = adapter.nfcAntennaInfo ?: return null

        val antennas = antennaInfo.availableNfcAntennas
        if (antennas.isEmpty()) return null

        val antenna = antennas[0]

        val locationX = antenna.locationX
        val locationY = antenna.locationY

        val deviceWidth = antennaInfo.deviceWidth
        val deviceHeight = antennaInfo.deviceHeight

        if (deviceWidth <= 0 || deviceHeight <= 0) return null

        // Calculate relative positions (0.0 = left/top, 1.0 = right/bottom)
        val relativeX = (locationX.toFloat() / deviceWidth.toFloat()).coerceIn(0f, 1f)
        val relativeY = (locationY.toFloat() / deviceHeight.toFloat()).coerceIn(0f, 1f)

        val orientation = if (deviceWidth > deviceHeight) 1 else 0

        return NfcAntennaPosition(
            orientation = orientation,
            x = relativeX,
            y = relativeY,
            z = 0,
        )
    }
}
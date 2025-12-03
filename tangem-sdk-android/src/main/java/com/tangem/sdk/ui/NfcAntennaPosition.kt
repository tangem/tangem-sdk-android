package com.tangem.sdk.ui

/**
 * Represents NFC antenna position with coordinates relative to device screen
 * @param orientation Screen orientation (0 = portrait, 1 = landscape)
 * @param x Horizontal position (0.0 = left, 1.0 = right)
 * @param y Vertical position (0.0 = top, 1.0 = bottom)
 * @param z Depth position (0 = front, 1 = back)
 */
data class NfcAntennaPosition(
    val orientation: Int,
    val x: Float,
    val y: Float,
    val z: Int,
)
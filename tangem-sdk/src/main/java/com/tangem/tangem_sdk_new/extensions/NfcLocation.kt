package com.tangem.tangem_sdk_new.extensions

import com.tangem.tangem_sdk_new.ui.NfcLocation

/**
[REDACTED_AUTHOR]
 */
fun NfcLocation.getX(): Float = this.x / 100f
fun NfcLocation.getY(): Float = this.y / 100f
fun NfcLocation.isHorizontal(): Boolean = orientation == 0
fun NfcLocation.isOnTheBack(): Boolean = z == 0
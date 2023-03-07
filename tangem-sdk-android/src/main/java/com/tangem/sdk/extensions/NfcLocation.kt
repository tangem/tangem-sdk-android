package com.tangem.sdk.extensions

import com.tangem.sdk.ui.NfcLocation

/**
[REDACTED_AUTHOR]
 */
fun NfcLocation.isHorizontal(): Boolean = orientation == 0
fun NfcLocation.isOnTheBack(): Boolean = z == 0
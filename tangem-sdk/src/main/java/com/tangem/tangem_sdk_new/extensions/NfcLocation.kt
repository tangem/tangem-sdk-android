package com.tangem.tangem_sdk_new.extensions

import com.tangem.tangem_sdk_new.ui.NfcLocation

/**
[REDACTED_AUTHOR]
 */
fun NfcLocation.isHorizontal(): Boolean = orientation == 0
fun NfcLocation.isOnTheBack(): Boolean = z == 0
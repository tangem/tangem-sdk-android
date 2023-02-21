package com.tangem.tangem_demo.ui.extension

import android.widget.TextView

/**
[REDACTED_AUTHOR]
 */
fun TextView.setTextFromClipboard() {
    context.getFromClipboard()?.let { this.text = it }
}
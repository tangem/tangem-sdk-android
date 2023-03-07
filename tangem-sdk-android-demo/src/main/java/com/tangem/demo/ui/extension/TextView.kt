package com.tangem.demo.ui.extension

import android.widget.TextView

/**
[REDACTED_AUTHOR]
 */
fun TextView.setTextFromClipboard() {
    context.getFromClipboard()?.let { this.text = it }
}
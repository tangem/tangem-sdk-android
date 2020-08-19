package com.tangem.tangem_sdk_new.extensions

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

internal fun View.show() {
    this.visibility = View.VISIBLE
}

internal fun View.hide() {
    this.visibility = View.GONE
}

internal fun View.show(show: Boolean) {
    if (show) {
        if (visibility != View.VISIBLE) visibility = View.VISIBLE
    } else {
        if (visibility != View.GONE) visibility = View.GONE
    }
}

fun View.dpToPx(dp: Float): Float = context.dpToPx(dp)
fun View.pxToDp(px: Float): Float = context.pxToDp(px)

fun View.showSoftKeyboard() {
    if (requestFocus()) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }
}

fun View.hideSoftKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(this.windowToken, 0)
}
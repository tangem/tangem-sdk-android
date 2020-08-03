package com.tangem.tangem_sdk_new.extensions

import android.content.Context

/**
[REDACTED_AUTHOR]
 */
fun Context.dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
fun Context.pxToDp(px: Float): Float = Math.round(px / resources.displayMetrics.density).toFloat()
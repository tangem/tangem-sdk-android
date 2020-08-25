package com.tangem.tangem_sdk_new.extensions

import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat

/**
[REDACTED_AUTHOR]
 */

fun ImageView.setVectorDrawable(@DrawableRes id: Int) {
    val drawable = VectorDrawableCompat.create(context.resources, id, context.theme)
    setImageDrawable(drawable)
}

fun ImageView.setAnimVectorDrawable(@DrawableRes id: Int) {
    val drawable = AnimatedVectorDrawableCompat.create(context, id)
    setImageDrawable(drawable)
}
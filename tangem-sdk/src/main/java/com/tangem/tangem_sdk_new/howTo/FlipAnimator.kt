package com.tangem.tangem_sdk_new.howTo

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import androidx.core.animation.doOnEnd

/**
[REDACTED_AUTHOR]
 */
class FlipAnimator(
    private val frontView: View,
    private val backView: View,
    val flipDuration: Long
) {
    var onEnd: (() -> Unit)? = null

    private var animatorSet: AnimatorSet? = null
    private var isBackViewVisible = false

    fun animate() {
        animatorSet = if (!isBackViewVisible) {
            isBackViewVisible = true
            AnimatorSet().apply {
                playTogether(
                    frontView.flipStart(flipDuration),
                    frontView.wiggleToRight(flipDuration),
                    backView.flipEnd(flipDuration),
                    backView.wiggleToRight(flipDuration)
                )
            }
        } else {
            isBackViewVisible = false
            AnimatorSet().apply {
                playTogether(
                    frontView.flipEnd(flipDuration),
                    frontView.wiggleToRight(flipDuration),
                    backView.flipStart(flipDuration),
                    backView.wiggleToRight(flipDuration)
                )
            }
        }
        animatorSet?.doOnEnd { onEnd?.invoke() }
        animatorSet?.start()
    }

    fun cancel() {
        onEnd = null
        animatorSet?.cancel()
    }
}

private fun View.flipStart(duration: Long): AnimatorSet {
    val rotation = ObjectAnimator.ofFloat(this, View.ROTATION_Y, 0f, -180f)
    rotation.duration = duration
    val fadeIn = ObjectAnimator.ofFloat(this, View.ALPHA, 1f, 0f)
    fadeIn.duration = 0
    fadeIn.startDelay = duration / 2
    return AnimatorSet().apply { playTogether(rotation, fadeIn) }
}

private fun View.flipEnd(duration: Long): AnimatorSet {
    val rotation = ObjectAnimator.ofFloat(this, View.ROTATION_Y, 180f, 0f)
    rotation.duration = duration
    rotation.repeatMode = ValueAnimator.REVERSE

    val fadeOut = ObjectAnimator.ofFloat(this, View.ALPHA, 0f, 1f)
    fadeOut.duration = 0
    fadeOut.startDelay = duration / 2

    return AnimatorSet().apply { playTogether(rotation, fadeOut) }
}

private fun View.wiggleToRight(duration: Long, distance: Float = 100f): AnimatorSet {
    val translateRight = ObjectAnimator.ofFloat(this, View.TRANSLATION_X, 0f, distance)
    val translateBack = ObjectAnimator.ofFloat(this, View.TRANSLATION_X, distance, 0f)
    return AnimatorSet().apply {
        this.duration = duration / 2
        playSequentially(translateRight, translateBack)
    }
}
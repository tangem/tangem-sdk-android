package com.tangem.tangem_sdk_new.howTo

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.addListener
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart

/**
[REDACTED_AUTHOR]
 */
abstract class TapAnimator(
    protected val view: View,
    val startOffsetPx: Float,
    val endOffsetPx: Float,
    val repeat: Int = -1,
) {
    var onRepeatsFinished: (() -> Unit)? = null

    private var tapAnimator: AnimatorSet? = null
    private var repeatedCount = 0

    fun animate() {
        tapAnimator?.cancel()
        tapAnimator = AnimatorSet()
        tapAnimator?.playSequentially(tapInAnimation(), downTime(3000), tapOutAnimation(), downTime(400))

        var isCancelled = false
        tapAnimator?.addListener(
            onStart = { isCancelled = false },
            onEnd = {
                repeatedCount++
                if (!isCancelled) {
                    if (repeat == -1 || repeatedCount < repeat) {
                        tapAnimator?.start()
                    } else {
                        onRepeatsFinished?.invoke()
                    }
                }
            },
            onCancel = { isCancelled = true },
            onRepeat = { isCancelled = false }
        )
        tapAnimator?.start()
    }

    fun cancel() {
        tapAnimator?.cancel()
        tapAnimator = null
    }

    private fun downTime(duration: Long): Animator {
        return ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1f).apply { this.duration = duration }
    }

    protected abstract fun tapInAnimation(): AnimatorSet

    protected abstract fun tapOutAnimation(): AnimatorSet
}

class TapBackAnimator(
    view: View,
    startOffsetPx: Float,
    endOffsetPx: Float,
    repeat: Int = -1
) : TapAnimator(view, startOffsetPx, endOffsetPx, repeat) {

    override fun tapInAnimation(): AnimatorSet {
        val scaleUpX = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.5f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.5f, 1f)
        val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f)
        val xToRight = ObjectAnimator.ofFloat(view, View.TRANSLATION_X,
            startOffsetPx, view.translationX + endOffsetPx)
        xToRight.interpolator = DecelerateInterpolator()

        val animator = AnimatorSet()
        animator.duration = 1200
        animator.playTogether(scaleUpX, scaleUpY, xToRight, alpha)
        return animator
    }

    override fun tapOutAnimation(): AnimatorSet {
        val scaleUpX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 0.5f)
        val scaleUpY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 0.5f)
        val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f)
        val xToLeft = ObjectAnimator.ofFloat(view, View.TRANSLATION_X,
            view.translationX + endOffsetPx, startOffsetPx)
        xToLeft.interpolator = AccelerateInterpolator()

        val animator = AnimatorSet()
        animator.duration = 1200
        animator.playTogether(scaleUpX, scaleUpY, xToLeft, alpha)
        return animator
    }
}

class TapFrontAnimator(
    view: View,
    startOffsetPx: Float,
    endOffsetPx: Float,
    repeat: Int = -1
) : TapAnimator(view, startOffsetPx, endOffsetPx, repeat) {

    init {
        val distance = 2500
        val scale: Float = view.resources.displayMetrics.density * distance
        view.cameraDistance = scale
    }

    override fun tapInAnimation(): AnimatorSet {
        val scaleUpX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1.5f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1.5f, 1f)
        val slideRight = ObjectAnimator.ofFloat(view, View.TRANSLATION_X,
            startOffsetPx, view.translationX + endOffsetPx)
        val slideUp = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y,
            view.translationY + 400f, view.translationY)
        slideRight.interpolator = DecelerateInterpolator()
        slideUp.interpolator = DecelerateInterpolator()

        val animator = AnimatorSet()
        animator.duration = 1200
        animator.playTogether(scaleUpX, scaleUpY, slideRight, slideUp)
        animator.doOnStart { view.alpha = 1f }
        return animator
    }

    override fun tapOutAnimation(): AnimatorSet {
        val scaleUpX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.5f)
        val scaleUpY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.5f)
        val slideLeft = ObjectAnimator.ofFloat(view, View.TRANSLATION_X,
            view.translationX + endOffsetPx, startOffsetPx)
        val slideDown = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y,
            view.translationY, view.translationY + 400f)
        slideLeft.interpolator = AccelerateInterpolator()
        slideDown.interpolator = AccelerateInterpolator()

        val animator = AnimatorSet()
        animator.duration = 1200
        animator.playTogether(scaleUpX, scaleUpY, slideLeft, slideDown)
        animator.doOnEnd { view.alpha = 0f }
        return animator
    }
}
package com.tangem.tangem_sdk_new.ui

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Build
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.animation.addListener
import com.tangem.tangem_sdk_new.extensions.dpToPx

class TouchCardAnimation(private var context: Context,
                         private var ivHandCardHorizontal: ImageView,
                         private var ivHandCardVertical: ImageView,
                         private var llHand: LinearLayout,
                         private var llNfc: LinearLayout) {

    companion object {
        const val CARD_ON_BACK = 0
        const val CARD_ON_FRONT = 1
        const val CARD_ORIENTATION_HORIZONTAL = 0
        const val CARD_ORIENTATION_VERTICAL = 1
    }

    var orientation: Int = 0
    var fullName: String = ""
    var x: Float = 0.toFloat()
    var y: Float = 0.toFloat()
    var z: Int = 0

    var onCardOnBack: (() -> Unit)? = null
    var onCardMoveOut: (() -> Unit)? = null

    private var handAnimator: AnimatorSet? = null

    fun init() {
        getAntennaLocation()
        setCardOrientation()
    }

    fun animate() {
        handAnimator?.cancel()
        handAnimator = AnimatorSet()
        handAnimator?.playSequentially(backInAnimation(), downTime(3000), backOutAnimation(), downTime(400))

        var isCancelled = false
        handAnimator?.addListener(
            onStart = { isCancelled = false },
            onEnd = {
                if (!isCancelled) handAnimator?.start()
            },
            onCancel = { isCancelled = true },
            onRepeat = { isCancelled = false }
        )
        handAnimator?.start()
    }

    fun stopAnimation() {
        handAnimator?.cancel()
    }

    private fun downTime(duration: Long): Animator {
        return ObjectAnimator.ofFloat(llHand, View.SCALE_X, 1f, 1f).apply { this.duration = duration }
    }

    private fun backInAnimation(): AnimatorSet {
        val dp = context.resources.displayMetrics.density
        llHand.translationY = (dp * (-50 + y * 250))
        llNfc.translationY = (dp * (-105 + y * 250))

        val scaleUpX = ObjectAnimator.ofFloat(llHand, View.SCALE_X, 0.5f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(llHand, View.SCALE_Y, 0.5f, 1f)
        val xToRight = ObjectAnimator.ofFloat(llHand, View.TRANSLATION_X, context.dpToPx(-75f), context.dpToPx(65f))
        val alpha = ObjectAnimator.ofFloat(llHand, View.ALPHA, 0f, 1f)
        xToRight.interpolator = DecelerateInterpolator()
        xToRight.addListener(onEnd = { onCardOnBack?.invoke() })

        val animator = AnimatorSet()
        animator.duration = 1200
        animator.playTogether(scaleUpX, scaleUpY, xToRight, alpha)
        return animator
    }

    private fun backOutAnimation(): AnimatorSet {
        val dp = context.resources.displayMetrics.density
        llHand.translationY = (dp * (-50 + y * 250))
        llNfc.translationY = (dp * (-105 + y * 250))

        val scaleUpX = ObjectAnimator.ofFloat(llHand, View.SCALE_X, 1f, 0.5f)
        val scaleUpY = ObjectAnimator.ofFloat(llHand, View.SCALE_Y, 1f, 0.5f)
        val xToLeft = ObjectAnimator.ofFloat(llHand, View.TRANSLATION_X, context.dpToPx(65f), context.dpToPx(-75f))
        val alpha = ObjectAnimator.ofFloat(llHand, View.ALPHA, 1f, 0f)
        xToLeft.interpolator = AccelerateInterpolator()
        xToLeft.addListener(onStart = { onCardMoveOut?.invoke() })

        val animator = AnimatorSet()
        animator.duration = 1200
        animator.playTogether(scaleUpX, scaleUpY, xToLeft, alpha)
        return animator
    }

    private fun getAntennaLocation() {
        val codename = Build.DEVICE

        // default values
        this.orientation = 0
        this.fullName = ""
        this.x = 0.5f
        this.y = 0.35f
        this.z = 0

        for (nfcLocation in NfcLocation.values()) {
            if (codename.startsWith(nfcLocation.codename)) {
                this.fullName = nfcLocation.fullName
                this.orientation = nfcLocation.orientation
                this.x = nfcLocation.x / 100f
                this.y = nfcLocation.y / 100f
                this.z = nfcLocation.z
            }
        }
    }

    private fun setCardOrientation() {
        when (orientation) {
            CARD_ORIENTATION_HORIZONTAL -> {
                ivHandCardHorizontal.visibility = View.VISIBLE
                ivHandCardVertical.visibility = View.GONE
            }

            CARD_ORIENTATION_VERTICAL -> {
                ivHandCardVertical.visibility = View.VISIBLE
                ivHandCardHorizontal.visibility = View.GONE
            }
        }

        // set card z position
        when (z) {
            CARD_ON_BACK -> llHand.elevation = 0.0f
            CARD_ON_FRONT -> llHand.elevation = 30.0f
        }
    }
}
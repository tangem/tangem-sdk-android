package com.tangem.tangem_sdk_new.ui.widget

import android.animation.Animator
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import com.skyfishjy.library.RippleBackground
import com.tangem.tangem_sdk_new.R
import com.tangem.tangem_sdk_new.SessionViewDelegateState
import com.tangem.tangem_sdk_new.extensions.dpToPx
import com.tangem.tangem_sdk_new.extensions.fadeOut
import com.tangem.tangem_sdk_new.extensions.hide
import com.tangem.tangem_sdk_new.extensions.isOnTheBack
import com.tangem.tangem_sdk_new.ui.NfcLocation
import com.tangem.tangem_sdk_new.ui.animation.AnimationProperty
import com.tangem.tangem_sdk_new.ui.animation.TapAnimationCallback
import com.tangem.tangem_sdk_new.ui.animation.TouchCardAnimation
import com.tangem.tangem_sdk_new.ui.animation.TouchCardAnimation.Companion.calculateRelativePosition

/**
[REDACTED_AUTHOR]
 */
class TouchCardWidget(
    mainView: View,
    private val nfcLocation: NfcLocation
) : BaseSessionDelegateStateWidget(mainView) {

    private val rippleBackgroundNfc = mainView.findViewById<RippleBackground>(R.id.rippleBackgroundNfc)
    private val ivHandCardHorizontal = mainView.findViewById<ImageView>(R.id.ivHandCardHorizontal)
    private val ivHandCardVertical = mainView.findViewById<ImageView>(R.id.ivHandCardVertical)
    private val ivPhone = mainView.findViewById<ImageView>(R.id.ivPhone)

    private val touchCardAnimation = TouchCardAnimation(
        ivPhone,
        ivHandCardHorizontal,
        ivHandCardVertical,
        AnimationProperty(mainView.dpToPx(-160f), mainView.dpToPx(-70f), mainView.dpToPx(150f), repeatCount = -1),
        nfcLocation
    )

    init {
        rippleBackgroundNfc.alpha = 0f
    }

    override fun setState(params: SessionViewDelegateState) {
        when (params) {
            is SessionViewDelegateState.Ready -> animate()
            is SessionViewDelegateState.TagLost -> animate()
            else -> stopAnimation()
        }
    }

    private fun animate() {
        setCallbacks()
        ivPhone.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                ivPhone.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val rippleElevation = if (nfcLocation.isOnTheBack()) ivPhone.elevation - 1 else ivPhone.elevation + 1
                rippleBackgroundNfc.elevation = rippleElevation
                rippleBackgroundNfc.translationX = calculateRelativePosition(nfcLocation.x, ivPhone.width)
                rippleBackgroundNfc.translationY = calculateRelativePosition(nfcLocation.y, ivPhone.height)
                touchCardAnimation.animate()
            }
        })
    }

    private fun setCallbacks() {
        touchCardAnimation.tapAnimationCallback = TapAnimationCallback(
            onTapInFinished = {
                rippleBackgroundNfc.alpha = 1f
                rippleBackgroundNfc.startRippleAnimation()

            },
            onTapOutStarted = {
                rippleBackgroundNfc.fadeOut(800) { rippleBackgroundNfc.stopRippleAnimation() }
            }
        )
        touchCardAnimation.animatorCallback = object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {}
            override fun onAnimationCancel(animation: Animator) {
                rippleBackgroundNfc.stopRippleAnimation()
                rippleBackgroundNfc.hide()
            }

            override fun onAnimationRepeat(animation: Animator) {}
        }
    }

    private fun stopAnimation() {
        rippleBackgroundNfc.stopRippleAnimation()
        touchCardAnimation.cancel()
    }

    override fun onBottomSheetDismiss() {
        stopAnimation()
    }
}

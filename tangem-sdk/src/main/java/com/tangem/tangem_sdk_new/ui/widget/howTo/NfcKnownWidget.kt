package com.tangem.tangem_sdk_new.ui.widget.howTo

import android.content.res.ColorStateList
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.transition.TransitionManager
import com.tangem.tangem_sdk_new.R
import com.tangem.tangem_sdk_new.extensions.*
import com.tangem.tangem_sdk_new.ui.NfcLocation
import com.tangem.tangem_sdk_new.ui.animation.AnimationProperty
import com.tangem.tangem_sdk_new.ui.animation.FlipAnimator
import com.tangem.tangem_sdk_new.ui.animation.Side
import com.tangem.tangem_sdk_new.ui.animation.TouchCardAnimation
import com.tangem.tangem_sdk_new.ui.animation.TouchCardAnimation.Companion.calculateRelativePosition


/**
[REDACTED_AUTHOR]
 */
class NfcKnownWidget(
    mainView: View,
    private val nfcLocation: NfcLocation,
) : NfcHowToWidget(mainView) {

    private val FLIP_DURATION = 650L
    private val FADE_DURATION = 400L

    private val phoneBack: ImageView = mainView.findViewById(R.id.imvPhoneBack)
    private val nfcBadge: ImageView = mainView.findViewById(R.id.imvNfcBadge)

    private val touchCardAnimation = TouchCardAnimation(
        phone,
        mainView.findViewById(R.id.imvHandWithCardH),
        mainView.findViewById(R.id.imvHandWithCardV),
        AnimationProperty(context.dpToPx(-395f), context.dpToPx(-95f), context.dpToPx(200f)),
        nfcLocation
    )
    private val animationScheduler = Handler()
    private val phoneFlipAnimator: FlipAnimator = FlipAnimator(phone, phoneBack, FLIP_DURATION)

    init {
        changeCameraDistance()
    }

    private fun changeCameraDistance() {
        val distance = 2500
        val scale: Float = mainView.resources.displayMetrics.density * distance
        phone.cameraDistance = scale
        phoneBack.cameraDistance = scale
    }

    override fun setState(params: HowToState) {
        when (params) {
            HowToState.Init -> {
                setMainButtonText(R.string.common_cancel)
                isCancelled = false
                setDefaultElevations()
                btnCancel.setOnClickListener { onOk?.invoke() }
                touchCardAnimation.hideTouchView(0)
            }
            HowToState.Animate -> {
                prepareInitialState()
                animationScheduler.postDelayed(::showNfcPosition, 3000)
                animationScheduler.postDelayed(::tapToKnownPosition, 9000)
            }
            HowToState.AntennaFound -> {
                if (currentState == HowToState.AntennaFound) return

                antennaIsFound()
            }
            HowToState.Cancel -> {
                cancelAndRemoveCallbacks()
            }
        }
        currentState = params
    }

    private fun cancelAndRemoveCallbacks() {
        isCancelled = true
        animationScheduler.removeCallbacksAndMessages(null)
        touchCardAnimation.cancel()
        phoneFlipAnimator.cancel()
    }

    private fun setDefaultElevations() {
        // touchViewElevation = 0 or 4
        val phoneElevation = context.dpToPx(2f)
        val badgeElevation = context.dpToPx(3f)
        phone.elevation = phoneElevation
        phoneBack.elevation = phoneElevation
        rippleView.elevation = badgeElevation
        nfcBadge.elevation = badgeElevation
    }

    private fun translateViewToNfcLocation(view: View) {
        view.translationX = calculateRelativePosition(nfcLocation.x, phone.width)
        view.translationY = calculateRelativePosition(nfcLocation.y, phone.height)
    }

    private fun prepareInitialState() {
        setText(R.string.how_to_known_here_how_to_use)
        TransitionManager.beginDelayedTransition(mainView as ViewGroup)
        phone.hide()
        phoneFlipAnimator.animateToSide(Side.FRONT, 0)
        phone.show()
        rippleView.alpha = 0f
        nfcBadge.alpha = 0f
    }

    private fun showNfcPosition() {
        setText(R.string.how_to_known_antenna_is_here)
        val fadeIn = {
            val duration = 1000L
            rippleView.fadeIn(duration)
            nfcBadge.fadeIn(duration)
        }

        translateViewToNfcLocation(rippleView)
        translateViewToNfcLocation(nfcBadge)

        rippleView.startRippleAnimation()

        if (nfcLocation.isOnTheBack()) {
            val badgeColor = ContextCompat.getColor(context, R.color.nfc_badge_back)
            ImageViewCompat.setImageTintList(nfcBadge, ColorStateList.valueOf(badgeColor))
            phoneFlipAnimator.onEnd = fadeIn
            phoneFlipAnimator.animate()
        } else {
            val badgeColor = ContextCompat.getColor(context, R.color.nfc_badge_front)
            ImageViewCompat.setImageTintList(nfcBadge, ColorStateList.valueOf(badgeColor))
            fadeIn()
        }
    }

    private fun tapToKnownPosition() {
        touchCardAnimation.onFinished = { if (!isCancelled) onFinished?.invoke() }

        nfcBadge.fadeOut(FADE_DURATION)
        rippleView.fadeOut(FADE_DURATION) {
            rippleView.stopRippleAnimation()
            if (nfcLocation.isOnTheBack()) {
                setText(R.string.how_to_known_tap_card_to_the_back)
                phoneFlipAnimator.onEnd = { touchCardAnimation.animate() }
                phoneFlipAnimator.animate()
            } else {
                setText(R.string.how_to_known_tap_card_to_the_front)
                touchCardAnimation.animate()
            }
        }
    }

    private fun antennaIsFound() {
        setText(R.string.how_to_nfc_detected)
        setMainButtonText(R.string.how_to_got_it_button)

        touchCardAnimation.cancel()
        nfcBadge.fadeOut(FADE_DURATION)
        rippleView.fadeOut(FADE_DURATION)
        rippleView.stopRippleAnimation()

        translateViewToNfcLocation(rippleView)
        translateViewToNfcLocation(nfcBadge)

        val showCardAndRippleView = {
            touchCardAnimation.showTouchViewAtNfcPosition(FADE_DURATION) {
                rippleView.elevation = if (nfcLocation.isOnTheBack()) 1f else rippleView.elevation
                rippleView.alpha = 1f
                rippleView.startRippleAnimation()
            }
        }

        val flipToFrontAndShow = {
            cancelAndRemoveCallbacks()
            if (phoneFlipAnimator.visibleSide == Side.FRONT) {
                showCardAndRippleView()
            } else {
                phoneFlipAnimator.onEnd = showCardAndRippleView
                phoneFlipAnimator.animateToSide(Side.FRONT, FLIP_DURATION)
            }
        }

        if (phoneFlipAnimator.animationInProgress) {
            phoneFlipAnimator.onEnd = flipToFrontAndShow
        } else {
            flipToFrontAndShow()
        }
    }
}
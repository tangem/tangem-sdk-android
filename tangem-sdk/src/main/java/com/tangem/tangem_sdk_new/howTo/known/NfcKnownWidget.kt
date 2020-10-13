package com.tangem.tangem_sdk_new.howTo.known

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.widget.ImageView
import android.widget.TextSwitcher
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.transition.TransitionManager
import com.skyfishjy.library.RippleBackground
import com.tangem.tangem_sdk_new.R
import com.tangem.tangem_sdk_new.extensions.*
import com.tangem.tangem_sdk_new.howTo.HowToState
import com.tangem.tangem_sdk_new.ui.NfcLocation
import com.tangem.tangem_sdk_new.ui.animation.AnimationProperty
import com.tangem.tangem_sdk_new.ui.animation.FlipAnimator
import com.tangem.tangem_sdk_new.ui.animation.TouchCardAnimation
import com.tangem.tangem_sdk_new.ui.animation.TouchCardAnimation.Companion.calculateRelativePosition
import com.tangem.tangem_sdk_new.ui.widget.BaseStateWidget


/**
[REDACTED_AUTHOR]
 */
class NfcKnownWidget(
    mainView: View,
    private val nfcLocation: NfcLocation
) : BaseStateWidget<HowToState.Known>(mainView) {

    var onFinished: (() -> Unit)? = null

    private val context = mainView.context
    private val rippleView: RippleBackground = mainView.findViewById(R.id.rippleBg)
    private val tvSwitcher: TextSwitcher = mainView.findViewById(R.id.tvHowToSwitcher)
    private val phone: ImageView = mainView.findViewById(R.id.imvPhone)
    private val phoneBack: ImageView = mainView.findViewById(R.id.imvPhoneBack)
    private val nfcBadge: ImageView = mainView.findViewById(R.id.imvNfcBadge)
    private val touchCardAnimation = TouchCardAnimation(
        phone,
        mainView.findViewById(R.id.imvHandWithCardH),
        mainView.findViewById(R.id.imvHandWithCardV),
        AnimationProperty(context.dpToPx(-395f), context.dpToPx(-95f), context.dpToPx(200f)),
        nfcLocation
    )

    private val phoneFlipAnimator: FlipAnimator = FlipAnimator(phone, phoneBack, 650)

    init {
        initTextChangesAnimation()
        changeCameraDistance()
        setElevations()
    }

    private fun initTextChangesAnimation() {
        tvSwitcher.setInAnimation(context, android.R.anim.slide_in_left)
        tvSwitcher.setOutAnimation(context, android.R.anim.slide_out_right)
    }

    private fun changeCameraDistance() {
        val distance = 2500
        val scale: Float = mainView.resources.displayMetrics.density * distance
        phone.cameraDistance = scale
        phoneBack.cameraDistance = scale
    }

    private fun setElevations() {
        val cardElevation = context.dpToPx(1f)
        val badgeElevation = context.dpToPx(2f)
        phone.elevation = cardElevation
        phoneBack.elevation = cardElevation
        rippleView.elevation = badgeElevation
        nfcBadge.elevation = badgeElevation
    }

    override fun setState(params: HowToState.Known) {
        when (params) {
            HowToState.Known.Prepare -> prepare()
            HowToState.Known.ShowNfcPosition -> showNfcPosition()
            HowToState.Known.TapToKnownPosition -> tapToKnownPosition()
        }
    }

    private fun prepare() {
        setText(R.string.how_to_known_here_how_to_use)
        TransitionManager.beginDelayedTransition(mainView as ViewGroup)
        phone.show()
    }

    private fun showNfcPosition() {
        val fadeOut = {
            val duration = 1000L
            rippleView.fadeOut(duration)
            nfcBadge.fadeOut(duration)
        }

        rippleView.translationX = calculateRelativePosition(nfcLocation.getX(), phone.width)
        rippleView.translationY = calculateRelativePosition(nfcLocation.getY(), phone.height)

        nfcBadge.translationX = calculateRelativePosition(nfcLocation.getX(), phone.width)
        nfcBadge.translationY = calculateRelativePosition(nfcLocation.getY(), phone.height)

        rippleView.startRippleAnimation()
        setText(R.string.how_to_known_antenna_is_here)

        if (nfcLocation.isOnTheBack()) {
            val badgeColor = ContextCompat.getColor(context, R.color.nfc_badge_back)
            ImageViewCompat.setImageTintList(nfcBadge, ColorStateList.valueOf(badgeColor))
            phoneFlipAnimator.onEnd = { fadeOut() }
            phoneFlipAnimator.animate()
        } else {
            val badgeColor = ContextCompat.getColor(context, R.color.nfc_badge_front)
            ImageViewCompat.setImageTintList(nfcBadge, ColorStateList.valueOf(badgeColor))
            fadeOut()
        }
    }


    private fun tapToKnownPosition() {
        touchCardAnimation.onTapAnimationFinished = { onFinished?.invoke() }

        val duration = 400L
        nfcBadge.fadeIn(duration)
        rippleView.fadeIn(duration) {
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

    private fun setText(textId: Int) {
        tvSwitcher.setText(tvSwitcher.context.getString(textId))
    }
}

private fun View.fadeOut(fadeOutDuration: Long, delayStart: Long = 0, onEnd: (() -> Unit)? = null) {
    animate().apply {
        alpha(1f)
        startDelay = delayStart
        duration = fadeOutDuration
        interpolator = AccelerateDecelerateInterpolator()
        withEndAction { onEnd?.invoke() }
    }.start()
}

private fun View.fadeIn(fadeInDuration: Long, delayStart: Long = 0, onEnd: (() -> Unit)? = null) {
    animate().apply {
        this.alpha(0f)
        startDelay = delayStart
        duration = fadeInDuration
        interpolator = AccelerateInterpolator()
        withEndAction { onEnd?.invoke() }
    }.start()
}
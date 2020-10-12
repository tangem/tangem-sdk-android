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
import com.tangem.tangem_sdk_new.extensions.dpToPx
import com.tangem.tangem_sdk_new.extensions.show
import com.tangem.tangem_sdk_new.howTo.*
import com.tangem.tangem_sdk_new.ui.widget.BaseStateWidget


/**
[REDACTED_AUTHOR]
 */
class NfcKnownWidget(
    mainView: View,
    private val nfcLocationProvider: NfcLocationProvider
) : BaseStateWidget<HowToState.Known>(mainView) {

    var onFinished: (() -> Unit)? = null

    private val context = mainView.context
    private val handWithCardViewHelper = CardViewHelper(isNfcHorizontal(), isNfcOnTheBack())

    private val handWithCard: View = mainView.findViewById(handWithCardViewHelper.getCardViewId())
    private val rippleView: RippleBackground = mainView.findViewById(R.id.rippleBg)
    private val tvSwitcher: TextSwitcher = mainView.findViewById(R.id.tvHowToSwitcher)
    private val phone: ImageView = mainView.findViewById(R.id.imvPhone)
    private val phoneBack: ImageView = mainView.findViewById(R.id.imvPhoneBack)
    private val nfcBadge: ImageView = mainView.findViewById(R.id.imvNfcBadge)

    private val phoneFlipAnimator: FlipAnimator = FlipAnimator(phone, phoneBack, 650)
    private var tapInOutAnimator: TapAnimator? = null

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
        handWithCard.elevation = handWithCard.dpToPx(handWithCardViewHelper.getCardViewElevation())
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

        val nfcLocation = nfcLocationProvider.getLocation()
        rippleView.translationX = calculateRelativePosition(nfcLocation.x, phone.width)
        rippleView.translationY = calculateRelativePosition(nfcLocation.y, phone.height)

        nfcBadge.translationX = calculateRelativePosition(nfcLocation.x, phone.width)
        nfcBadge.translationY = calculateRelativePosition(nfcLocation.y, phone.height)

        rippleView.startRippleAnimation()
        setText(R.string.how_to_known_antenna_is_here)

        if (isNfcOnTheBack()) {
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
        handWithCardViewHelper.translateToNfcLocation(handWithCard, phone, nfcLocationProvider.getLocation())
        tapInOutAnimator = createTapAnimator(handWithCard)
        tapInOutAnimator?.onRepeatsFinished = { onFinished?.invoke() }

        val duration = 400L
        nfcBadge.fadeIn(duration)
        rippleView.fadeIn(duration) {
            rippleView.stopRippleAnimation()
            if (isNfcOnTheBack()) {
                setText(R.string.how_to_known_tap_card_to_the_back)
                phoneFlipAnimator.onEnd = { tapInOutAnimator?.animate() }
                phoneFlipAnimator.animate()
            } else {
                setText(R.string.how_to_known_tap_card_to_the_front)
                tapInOutAnimator?.animate()
            }
        }
    }

    private fun setText(textId: Int) {
        tvSwitcher.setText(tvSwitcher.context.getString(textId))
    }

    private fun createTapAnimator(view: View): TapAnimator {
        val animProperties = AnimationProperty(context.dpToPx(-395f), context.dpToPx(-95f))
        return if (isNfcOnTheBack()) {
            TapBackAnimator(view, animProperties)
        } else {
            TapFrontAnimator(view, animProperties, context.dpToPx(200f))
        }
    }

    private fun isNfcOnTheBack(): Boolean = nfcLocationProvider.getLocation().z == 0

    private fun isNfcHorizontal(): Boolean = nfcLocationProvider.getOrientation() == Orientation.HORIZONTAL
}

// Only for views centered in a relative view. Works with positioning on x and y vectors
fun calculateRelativePosition(location: Float, sizePx: Int): Float {
    val positionInView = location * sizePx
    val translateToStartOfView = (sizePx / 2) * -1
    return translateToStartOfView + positionInView
}

private class CardViewHelper(
    private val isNfcHorizontal: Boolean,
    private val isNfcOnTheBack: Boolean
) {

    fun getCardViewId(): Int = if (isNfcHorizontal) R.id.imvHandWithCardH else R.id.imvHandWithCardV

    fun getCenterOfCardRelativeToSelf(): Float = if (isNfcHorizontal) 0.33f else 0.27f

    fun getCardViewElevation(): Float = if (isNfcOnTheBack) 0f else 3f

    fun translateToNfcLocation(handWithCard: View, inView: View, nfcLocation: NfcPoint) {
        val cardCenter = handWithCard.height * getCenterOfCardRelativeToSelf()
        handWithCard.translationX = calculateRelativePosition(nfcLocation.x, inView.width)
        handWithCard.translationY = calculateRelativePosition(nfcLocation.y, inView.height) +
            cardCenter
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
package com.tangem.tangem_sdk_new.ui.animation

import android.view.View
import com.tangem.tangem_sdk_new.extensions.*
import com.tangem.tangem_sdk_new.ui.NfcLocation

/**
[REDACTED_AUTHOR]
 */
class TouchCardAnimation(
    private val phone: View,
    private val cardHorizontal: View,
    private val cardVertical: View,
    private val animationProperty: AnimationProperty,
    private val nfcLocation: NfcLocation
) {

    var onTapAnimationFinished: OnTapAnimationFinished? = null
    var tapAnimationCallback: TapAnimationCallback? = null
        set(value) {
            field = value
            tapAnimator?.animationCallback = value
        }

    private val tappedView: View = if (nfcLocation.isHorizontal()) cardHorizontal else cardVertical
    private var tapAnimator: TapAnimator? = null

    init {
        tappedView.elevation = tappedView.dpToPx(getCardViewElevation())
    }

    fun animate() {
        tapAnimator?.cancel()
        translateToNfcLocation(tappedView, phone, nfcLocation)
        tapAnimator = TapAnimator.create(tappedView, nfcLocation.isOnTheBack(), animationProperty)
        tapAnimator?.animationCallback = tapAnimationCallback
        tapAnimator?.onRepeatsFinished = { onTapAnimationFinished?.invoke() }
        tapAnimator?.animate()
    }

    fun cancel() {
        tapAnimationCallback = null
        tapAnimator?.cancel()
    }

    private fun getCardViewElevation(): Float = if (nfcLocation.isOnTheBack()) 0f else 3f

    private fun translateToNfcLocation(handWithCard: View, inView: View, nfcLocation: NfcLocation) {
        val cardCenter = handWithCard.height * getCenterOfCardRelativeToSelf()
        handWithCard.translationX = calculateRelativePosition(nfcLocation.getX(), inView.width)
        handWithCard.translationY = calculateRelativePosition(nfcLocation.getY(), inView.height) +
            cardCenter
    }

    private fun getCenterOfCardRelativeToSelf(): Float = if (nfcLocation.isHorizontal()) 0.33f else 0.27f

    companion object {
        // Only for views centered in a relative view. Works with positioning on x and y vectors
        fun calculateRelativePosition(location: Float, sizePx: Int): Float {
            val positionInView = location * sizePx
            val translateToStartOfView = (sizePx / 2) * -1
            return translateToStartOfView + positionInView
        }
    }
}

typealias VoidCallback = () -> Unit
typealias OnTapAnimationFinished = VoidCallback
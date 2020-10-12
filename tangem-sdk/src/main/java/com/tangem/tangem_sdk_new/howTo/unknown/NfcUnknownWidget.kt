package com.tangem.tangem_sdk_new.howTo.unknown

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.TextSwitcher
import androidx.core.animation.addListener
import androidx.core.animation.doOnStart
import com.skyfishjy.library.RippleBackground
import com.tangem.tangem_sdk_new.R
import com.tangem.tangem_sdk_new.extensions.dpToPx
import com.tangem.tangem_sdk_new.extensions.vibrate
import com.tangem.tangem_sdk_new.howTo.HowToState
import com.tangem.tangem_sdk_new.postUI
import com.tangem.tangem_sdk_new.ui.widget.BaseStateWidget

/**
[REDACTED_AUTHOR]
 */
class NfcUnknownWidget(
    mainView: View
) : BaseStateWidget<HowToState.Unknown>(mainView) {

    private val handWithCard: View = mainView.findViewById(R.id.imvHandWithCard)
    private val rippleView: RippleBackground = mainView.findViewById(R.id.rippleBg)
    private val tvSwitcher: TextSwitcher = mainView.findViewById(R.id.tvHowToSwitcher)

    private val animatorSet: AnimatorSet = AnimatorSet()
    private var currentState: HowToState.Unknown? = null

    init {
        initTextChangesAnimation()
        initHandAnimators()
    }

    private fun initTextChangesAnimation() {
        tvSwitcher.setInAnimation(mainView.context, android.R.anim.slide_in_left)
        tvSwitcher.setOutAnimation(mainView.context, android.R.anim.slide_out_right)
    }

    private fun initHandAnimators() {
        val list = mutableListOf<Animator>()
        list.add(getSlideRightAnimation())
        list.add(getSlideDownAnimation())
        list.add(getSlideLeftAnimation())
        animatorSet.playSequentially(list)
        animatorSet.addListener(
            onStart = { handWithCard.alpha = 1f },
            onEnd = {
                if (currentState == HowToState.Unknown.FindAntenna) {
                    postUI(100) { setState(HowToState.Unknown.FindAntenna) }
                }
            },
            onCancel = {
                setText(R.string.how_to_unknown_empty)
                ObjectAnimator.ofFloat(handWithCard, View.ALPHA, 1f, 0.0f).apply { duration = 1000 }.start()
                mainView.context.vibrate(longArrayOf(0, 100, 30, 350))
            }
        )
    }

    private fun getSlideRightAnimation(): Animator {
        val scaleUpX = ObjectAnimator.ofFloat(handWithCard, View.SCALE_X, 0.5f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(handWithCard, View.SCALE_Y, 0.5f, 1f)
        val xToRight = ObjectAnimator.ofFloat(handWithCard, View.TRANSLATION_X, dpToPx(-395f), dpToPx(-95f))
        xToRight.interpolator = DecelerateInterpolator()

        return AnimatorSet().apply {
            duration = 3000
            playTogether(scaleUpX, scaleUpY, xToRight)
            doOnStart { setText(R.string.how_to_unknown_tap_card) }
        }
    }

    private fun getSlideDownAnimation(): Animator {
        return ObjectAnimator.ofFloat(handWithCard, View.TRANSLATION_Y, dpToPx(-25f), dpToPx(145f)).apply {
            duration = 10500
            doOnStart { setText(R.string.how_to_unknown_move_card) }
        }
    }

    private fun getSlideLeftAnimation(): Animator {
        val scaleDownX = ObjectAnimator.ofFloat(handWithCard, View.SCALE_X, 1f, 0.5f)
        val scaleDownY = ObjectAnimator.ofFloat(handWithCard, View.SCALE_Y, 1f, 0.5f)

        val xToLeft = ObjectAnimator.ofFloat(handWithCard, View.TRANSLATION_X, dpToPx(-95f), dpToPx(-395f))
        xToLeft.interpolator = AccelerateInterpolator()

        return AnimatorSet().apply {
            duration = 3000
            playTogether(xToLeft, scaleDownX, scaleDownY)
            doOnStart { setText(R.string.how_to_unknown_nothing_happened) }
        }
    }

    private fun setText(textId: Int) {
        tvSwitcher.setText(tvSwitcher.context.getString(textId))
    }

    override fun setState(params: HowToState.Unknown) {
        currentState = params
        when (params) {
            HowToState.Unknown.FindAntenna -> {
                rippleView.stopRippleAnimation()
                animatorSet.start()
            }
            HowToState.Unknown.AntennaFound -> {
                animatorSet.cancel()
                rippleView.startRippleAnimation()
                setText(R.string.how_to_unknown_detected)
            }
            HowToState.Unknown.Cancel -> {
                animatorSet.end()
                animatorSet.cancel()
                setText(R.string.how_to_unknown_empty)
            }
        }
    }

    override fun onBottomSheetDismiss() {
    }

    private fun dpToPx(value: Float): Float = mainView.dpToPx(value)
}
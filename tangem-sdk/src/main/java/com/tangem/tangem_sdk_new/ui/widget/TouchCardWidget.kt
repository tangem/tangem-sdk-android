package com.tangem.tangem_sdk_new.ui.widget

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.skyfishjy.library.RippleBackground
import com.tangem.tangem_sdk_new.R
import com.tangem.tangem_sdk_new.SessionViewDelegateState
import com.tangem.tangem_sdk_new.extensions.setVectorDrawable
import com.tangem.tangem_sdk_new.ui.TouchCardAnimation

/**
[REDACTED_AUTHOR]
 */
class TouchCardWidget(mainView: View) : BaseSessionDelegateStateWidget(mainView) {

    private val rippleBackgroundNfc = mainView.findViewById<RippleBackground>(R.id.rippleBackgroundNfc)
    private val ivHandCardHorizontal = mainView.findViewById<ImageView>(R.id.ivHandCardHorizontal)
    private val ivHandCardVertical = mainView.findViewById<ImageView>(R.id.ivHandCardVertical)
    private val ivPhone = mainView.findViewById<ImageView>(R.id.ivPhone)
    private val llHand = mainView.findViewById<LinearLayout>(R.id.llHand)
    private val llNfc = mainView.findViewById<LinearLayout>(R.id.llNfc)

    private val nfcDeviceAntenna = TouchCardAnimation(mainView.context, ivHandCardHorizontal, ivHandCardVertical, llHand, llNfc)

    init {
        nfcDeviceAntenna.init()
        nfcDeviceAntenna.onCardOnBack = { rippleBackgroundNfc.startRippleAnimation() }
        nfcDeviceAntenna.onCardMoveOut = { rippleBackgroundNfc.stopRippleAnimation() }

        ivHandCardHorizontal.setVectorDrawable(R.drawable.hand_full_card_horizontal)
        ivHandCardVertical.setVectorDrawable(R.drawable.hand_full_card_vertical)
        ivPhone.setVectorDrawable(R.drawable.phone)
    }

    override fun setState(params: SessionViewDelegateState) {
        when (params) {
            is SessionViewDelegateState.Ready -> animate()
            is SessionViewDelegateState.TagLost -> animate()
            else -> stopAnimation()
        }
    }

    private fun animate() {
        nfcDeviceAntenna.animate()
    }

    private fun stopAnimation() {
        rippleBackgroundNfc.stopRippleAnimation()
        nfcDeviceAntenna.stopAnimation()
    }

    override fun onBottomSheetDismiss() {
        stopAnimation()
    }
}

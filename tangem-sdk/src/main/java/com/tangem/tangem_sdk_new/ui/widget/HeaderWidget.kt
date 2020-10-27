package com.tangem.tangem_sdk_new.ui.widget

import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.tangem.tangem_sdk_new.R
import com.tangem.tangem_sdk_new.SessionViewDelegateState
import com.tangem.tangem_sdk_new.extensions.hide
import com.tangem.tangem_sdk_new.extensions.hideSoftKeyboard
import com.tangem.tangem_sdk_new.extensions.setVectorDrawable
import com.tangem.tangem_sdk_new.extensions.show
import com.tangem.tangem_sdk_new.ui.animation.VoidCallback

/**
[REDACTED_AUTHOR]
 */
class HeaderWidget(
    mainView: View,
    private val howToIsEnabled: Boolean
) : BaseSessionDelegateStateWidget(mainView) {

    private val tvCard = mainView.findViewById<TextView>(R.id.tvCard)
    private val tvCardId = mainView.findViewById<TextView>(R.id.tvCardId)
    private val imvClose = mainView.findViewById<ImageView>(R.id.imvClose)
    private val btnHowTo = mainView.findViewById<Button>(R.id.btnHowTo)

    var onClose: VoidCallback? = null
    var onHowTo: VoidCallback? = null
    var isFullScreenMode: Boolean = false

    var cardId: String? = null
        private set

    init {
        imvClose.setVectorDrawable(R.drawable.ic_close)
        imvClose.setOnClickListener {
            mainView.hideSoftKeyboard()
            mainView.requestFocus()
            onClose?.invoke()
        }
        btnHowTo.show(howToIsEnabled)
        btnHowTo.setOnClickListener { onHowTo?.invoke() }
    }

    override fun showWidget(show: Boolean, withAnimation: Boolean) {

    }

    override fun setState(params: SessionViewDelegateState) {
        when (params) {
            is SessionViewDelegateState.Ready -> {
                cardId = params.cardId
                imvClose.hide()
                showHowToButton(true)
                tvCard.show()
                if (cardId == null) {
                    tvCard.text = getString(R.string.view_delegate_header_any_card)
                } else {
                    tvCard.text = getString(R.string.view_delegate_header_card)
                    tvCardId.show()
                    tvCardId.text = cardId!!.chunked(4).joinToString(" ")
                }
            }
            is SessionViewDelegateState.PinChangeRequested -> {
                showHowToButton(false)
                imvClose.show()
            }
            is SessionViewDelegateState.PinRequested -> {
                showHowToButton(false)
                imvClose.show(isFullScreenMode)
            }
            else -> {
                imvClose.hide()
                showHowToButton(true)
            }
        }
    }

    private fun showHowToButton(show: Boolean) {
        if (!howToIsEnabled) return

        btnHowTo.show(show)
    }
}
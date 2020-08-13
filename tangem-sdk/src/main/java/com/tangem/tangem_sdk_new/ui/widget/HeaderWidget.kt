package com.tangem.tangem_sdk_new.ui.widget

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.tangem.tangem_sdk_new.R
import com.tangem.tangem_sdk_new.SessionViewDelegateState
import com.tangem.tangem_sdk_new.extensions.hideSoftKeyboard
import com.tangem.tangem_sdk_new.extensions.show

/**
[REDACTED_AUTHOR]
 */
class HeaderWidget(mainView: View) : BaseSessionDelegateStateWidget(mainView) {

    private val tvCard = mainView.findViewById<TextView>(R.id.tvCard)
    private val tvCardId = mainView.findViewById<TextView>(R.id.tvCardId)
    private val imvClose = mainView.findViewById<ImageView>(R.id.imvClose)

    var onClose: (() -> Unit)? = null

    init {
        imvClose.setOnClickListener {
            mainView.hideSoftKeyboard()
            mainView.requestFocus()
            onClose?.invoke()
        }
    }

    override fun showWidget(show: Boolean, withAnimation: Boolean) {

    }

    override fun setState(params: SessionViewDelegateState) {
        when (params) {
            is SessionViewDelegateState.Ready -> {
                imvClose.show(false)
                params.cardId?.let {
                    tvCard.show(true)
                    tvCardId.show(true)
                    tvCardId.text = splitByLength(it, 4)
                }
            }
            is SessionViewDelegateState.PinChangeRequested -> {
                imvClose.show(true)
            }
            else -> {
                imvClose.show(false)
            }
        }
    }

    private fun splitByLength(value: String, sizeOfChunk: Int): String {
        val length = value.length
        if (length <= sizeOfChunk) return value

        val countOfFullSizedChunk = length / sizeOfChunk
        val builder = StringBuilder()
        var startPosition = 0
        for (i in 0 until countOfFullSizedChunk) {
            val endPosition = startPosition + sizeOfChunk
            builder.append(value.substring(startPosition, endPosition)).append(" ")
            startPosition = endPosition
        }
        return builder.append(value.substring(startPosition, length)).toString().trim()
    }
}
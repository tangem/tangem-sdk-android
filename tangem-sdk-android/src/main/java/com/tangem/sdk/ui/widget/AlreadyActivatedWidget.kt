package com.tangem.sdk.ui.widget

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.tangem.common.extensions.VoidCallback
import com.tangem.sdk.R
import com.tangem.sdk.SessionViewDelegateState

class AlreadyActivatedWidget(mainView: View) : BaseSessionDelegateStateWidget(mainView) {

    var onConfirm: VoidCallback? = null
    var onJustBoughtClick: VoidCallback? = null

    private val tvTitle = mainView.findViewById<TextView>(R.id.tvTitle)
    private val tvMessage = mainView.findViewById<TextView>(R.id.tvMessage)
    private val btnConfirm = mainView.findViewById<Button>(R.id.btnConfirm)
    private val tvWarningMessage = mainView.findViewById<TextView>(R.id.tvWarningMessage)
    private val llWarning = mainView.findViewById<LinearLayout>(R.id.warningView)
    private val btnJustBought = llWarning?.findViewById<Button>(R.id.btnJustBought)

    init {
        tvTitle.setText(R.string.already_activated_title)
        tvMessage.setText(R.string.already_activated_message)
        tvWarningMessage.setText(R.string.tangem_never_pregenerate_code_alert)

        btnConfirm.setOnClickListener { onConfirm?.invoke() }
        btnJustBought?.setOnClickListener { onJustBoughtClick?.invoke() }
    }

    override fun setState(params: SessionViewDelegateState) {
        /* No-op */
    }
}
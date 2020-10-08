package com.tangem.tangem_sdk_new.ui.widget

import android.view.View
import android.widget.Button
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.tangem.commands.PinType
import com.tangem.tangem_sdk_new.R
import com.tangem.tangem_sdk_new.SessionViewDelegateState
import com.tangem.tangem_sdk_new.extensions.hideSoftKeyboard
import com.tangem.tangem_sdk_new.extensions.showSoftKeyboard
import com.tangem.tangem_sdk_new.postUI

/**
[REDACTED_AUTHOR]
 */
class PinCodeRequestWidget(mainView: View) : BaseSessionDelegateStateWidget(mainView) {

    var onContinue: ((String) -> Unit)? = null

    private val tilPinCode = mainView.findViewById<TextInputLayout>(R.id.tilPinCode)
    private val etPinCode = mainView.findViewById<TextInputEditText>(R.id.etPinCode)
    private val btnContinue = mainView.findViewById<Button>(R.id.btnContinue)
    private val expandingView = mainView.findViewById<View>(R.id.expandingView)

    fun canExpand(): Boolean = expandingView != null

    override fun setState(params: SessionViewDelegateState) {
        when (params) {
            is SessionViewDelegateState.PinRequested -> {
                val code = when (params.pinType) {
                    PinType.Pin1 -> getString(R.string.pin1)
                    PinType.Pin2 -> getString(R.string.pin2)
                }
                tilPinCode.hint = code
                etPinCode.setText("")
                postUI(1000) { etPinCode.showSoftKeyboard() }
                btnContinue.setOnClickListener {
                    val pin = etPinCode.text.toString()
                    if (pin.isEmpty()) {
                        tilPinCode.error = mainView.context.getString(
                                R.string.pin_enter_format, code
                        )
                    } else {
                        tilPinCode.error = null
                        tilPinCode.isErrorEnabled = false
                        mainView.requestFocus()
                        etPinCode.hideSoftKeyboard()
                        postUI(250) { onContinue?.invoke(pin) }
                    }
                }
            }
        }
    }
}
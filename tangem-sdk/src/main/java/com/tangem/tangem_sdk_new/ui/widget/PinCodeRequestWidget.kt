package com.tangem.tangem_sdk_new.ui.widget

import android.view.View
import android.widget.Button
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.tangem.tangem_sdk_new.R
import com.tangem.tangem_sdk_new.SessionViewDelegateState
import com.tangem.tangem_sdk_new.extensions.hideSoftKeyboard
import com.tangem.tangem_sdk_new.extensions.showSoftKeyboard
import com.tangem.tangem_sdk_new.ui.widget.progressBar.StateWidget
import ru.gbixahue.eu4d.android.global.singleton.threadHandler.post

/**
[REDACTED_AUTHOR]
 */
class PinCodeRequestWidget(
    private val mainView: View
) : StateWidget<SessionViewDelegateState> {

    var onSave: ((String) -> Unit)? = null

    private val tilPinCode = mainView.findViewById<TextInputLayout>(R.id.tilPinCode)
    private val etPinCode = mainView.findViewById<TextInputEditText>(R.id.etPinCode)
    private val btnSave = mainView.findViewById<Button>(R.id.btnSaveCode)

    override fun getView(): View = mainView

    override fun apply(params: SessionViewDelegateState) {
        when (params) {
            is SessionViewDelegateState.PinRequested -> {
                if (params.message.isNotEmpty()) tilPinCode.hint = params.message

                etPinCode.showSoftKeyboard()
                btnSave.setOnClickListener {
                    val pin = etPinCode.text.toString()
                    if (pin.isEmpty()) {
                        tilPinCode.error = mainView.context.getString(R.string.pin_enter_error_empty)
                    } else {
                        tilPinCode.error = null
                        tilPinCode.isErrorEnabled = false
                        etPinCode.setText("")
                        etPinCode.hideSoftKeyboard()
                        mainView.requestFocus()
                        post(250) { onSave?.invoke(pin) }
                    }
                }
            }
        }
    }
}
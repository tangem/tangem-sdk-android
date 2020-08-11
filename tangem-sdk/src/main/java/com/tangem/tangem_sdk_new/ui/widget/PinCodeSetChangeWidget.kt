package com.tangem.tangem_sdk_new.ui.widget

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.tangem.tangem_sdk_new.R
import com.tangem.tangem_sdk_new.SessionViewDelegateState
import com.tangem.tangem_sdk_new.extensions.showSoftKeyboard
import com.tangem.tangem_sdk_new.ui.widget.progressBar.StateWidget

/**
[REDACTED_AUTHOR]
 */
class PinCodeModificationWidget(
    private val mainView: View,
    private var mode: Int
) : StateWidget<SessionViewDelegateState> {

    companion object {
        val MODE_SET = 0
        val MODE_CHANGE = 1

        private val MATCH = 1
        private val NOT_MATCH = 0
        private val UNDEFINED = -1
    }

    var onSave: ((String) -> Unit)? = null

    private val tvScreenTitle: TextView = mainView.findViewById(R.id.tvScreenTitle)

    private val tilPinCode: TextInputLayout = mainView.findViewById(R.id.tilPinCode)
    private val tilNewPinCode: TextInputLayout = mainView.findViewById(R.id.tilNewPinCode)
    private val tilPinCodeConfirm: TextInputLayout = mainView.findViewById(R.id.tilPinCodeConfirm)

    private val etPinCode: TextInputEditText = mainView.findViewById(R.id.etPinCode)
    private val etNewPinCode: TextInputEditText = mainView.findViewById(R.id.etNewPinCode)
    private val etPinCodeConfirm: TextInputEditText = mainView.findViewById(R.id.etPinCodeConfirm)

    private val btnSave: Button = mainView.findViewById(R.id.btnSaveCode)

    private var isPasswordEnabled = true
    private val isChangeCodeMode: Boolean = mode == MODE_CHANGE

    init {
        modifyUiByMode()
        attachCodesCheck(getEtForCodeChecks())
        attachCodesCheck(etPinCodeConfirm)
        btnSave.isEnabled = false

        tilPinCode.setEndIconOnClickListener {
            isPasswordEnabled = !isPasswordEnabled
            togglePasswordInputType(etPinCode)
            togglePasswordInputType(etNewPinCode)
            togglePasswordInputType(etPinCodeConfirm)
        }

        btnSave.setOnClickListener {
            mainView.requestFocus()
            onSave?.invoke(etPinCodeConfirm.text?.toString() ?: "")
        }
    }

    override fun getView(): View = mainView

    override fun apply(params: SessionViewDelegateState) {
        when (params) {
            is SessionViewDelegateState.PinChangeRequested -> {
                etPinCode.showSoftKeyboard()
            }
        }
    }

    fun switchModeTo(mode: Int) {
        this.mode = mode
        modifyUiByMode()
    }

    private fun modifyUiByMode() {
        if (isChangeCodeMode) {
            tvScreenTitle.text = getString(R.string.pin_change_access_code_title)
            tilPinCode.hint = getString(R.string.pin_change_current_access_code_hint)
            tilNewPinCode.hint = getString(R.string.pin_change_new_access_code_hint)
            tilPinCodeConfirm.hint = getString(R.string.pin_change_new_access_code_confirm_hint)
        } else {
            tvScreenTitle.text = getString(R.string.pin_set_access_code_title)
            tilPinCode.hint = getString(R.string.pin_set_access_code_hint)
            tilNewPinCode.visibility = View.GONE
            tilPinCodeConfirm.hint = getString(R.string.pin_set_access_code_hint)
        }
    }

    private fun attachCodesCheck(et: TextInputEditText) {
        DebounceAfterTextChanged(et) {
            val state = checkCodes()
            btnSave.isEnabled = state == MATCH
            updateErrorsVisibility(state == NOT_MATCH)
        }
    }

    private fun getTilForCodeChecks(): TextInputLayout = if (isChangeCodeMode) tilNewPinCode else tilPinCode
    private fun getEtForCodeChecks(): TextInputEditText = if (isChangeCodeMode) etNewPinCode else etPinCode

    private fun checkCodes(): Int {
        val code1 = getEtForCodeChecks().text?.toString() ?: ""
        val code2 = etPinCodeConfirm.text?.toString() ?: ""

        if (code1.isEmpty() || code2.isEmpty()) return UNDEFINED

        return if (code1 == code2) MATCH else NOT_MATCH
    }

    private fun updateErrorsVisibility(errorIsVisible: Boolean) {
        val til1 = getTilForCodeChecks()
        val til2 = tilPinCodeConfirm

        TransitionManager.beginDelayedTransition(mainView as ViewGroup, AutoTransition())
        if (errorIsVisible) {
            val errorMessage = getString(R.string.pin_access_code_confirm_error)
            til1.error = errorMessage
            til2.error = errorMessage
        } else {
            til1.error = null
            til2.error = null
            til1.isErrorEnabled = false
            til2.isErrorEnabled = false
        }
    }

    private fun togglePasswordInputType(et: TextInputEditText) {
        et.inputType = if (isPasswordEnabled) 129 else InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        if (et.isFocused) et.setSelection(et.text?.length ?: 0)
    }

    private fun getString(id: Int): String = mainView.context.getString(id)
}

class DebounceAfterTextChanged(
    val view: EditText,
    var debounce: Long = 300,
    var afterTextChanged: ((Editable?) -> Unit)? = null
) {

    var textWatcher: TextWatcher? = null
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val runnable = Runnable {
        afterTextChanged?.invoke(editable)
    }

    private var editable: Editable? = null

    init {
        textWatcher = view.addTextChangedListener(
            afterTextChanged = {
                editable = it
                handler.removeCallbacks(runnable)
                handler.postDelayed(runnable, debounce)
            }
        )
    }

    fun removeWatcher() {
        view.removeTextChangedListener(textWatcher)
    }
}
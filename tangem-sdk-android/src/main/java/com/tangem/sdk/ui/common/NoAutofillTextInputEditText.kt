package com.tangem.sdk.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.autofill.AutofillValue
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.google.android.material.textfield.TextInputEditText

/**
 * Text field kept away from the autofill framework and from the keyboard's personalized learning.
 *
 * `android:importantForAutofill="no"` stopped being enough on Android 14: for a view that is not
 * important the framework falls back to its own heuristics, and those treat every single-line
 * [android.widget.EditText] as autofillable, so the password saved for the app gets offered in the
 * access code field. Reporting no autofill type is checked before that fallback, which keeps the
 * field from starting an autofill session and marks it as non-fillable in the autofill structure.
 *
 * Screen capture and the assist structure are a separate channel, covered by `FLAG_SECURE` on the
 * dialog window.
 */
class NoAutofillTextInputEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.editTextStyle,
) : TextInputEditText(context, attrs, defStyleAttr) {

    override fun getAutofillType(): Int = View.AUTOFILL_TYPE_NONE

    override fun autofill(value: AutofillValue) = Unit

    // the field is the only place the flag survives: `imeOptions` gets assigned wholesale elsewhere
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val inputConnection = super.onCreateInputConnection(outAttrs)
        outAttrs.imeOptions = outAttrs.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        return inputConnection
    }
}
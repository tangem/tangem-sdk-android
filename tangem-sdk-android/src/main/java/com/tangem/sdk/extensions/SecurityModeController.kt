package com.tangem.sdk.extensions

import android.app.Dialog
import android.view.WindowManager
import com.tangem.Log

/**
 * Controller of security mode
 *
[REDACTED_AUTHOR]
 */
internal object SecurityModeController {

    var isEnabled: Boolean = false

    /**
     * Set security mode
     *
     * @param dialog dialog
     * @param value  enable or disable
     */
    fun setSecurityMode(dialog: Dialog, value: Boolean) {
        if (isEnabled == value) return

        Log.info { "SecurityModeController: mode is ${if (value) "enabled" else "disabled"}" }

        if (value) {
            dialog.window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        isEnabled = value
    }
}
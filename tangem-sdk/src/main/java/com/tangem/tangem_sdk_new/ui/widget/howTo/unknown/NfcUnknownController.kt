package com.tangem.tangem_sdk_new.ui.widget.howTo.unknown

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.tangem.tangem_sdk_new.nfc.NfcManager
import com.tangem.tangem_sdk_new.postUI
import com.tangem.tangem_sdk_new.ui.widget.howTo.HowToState

/**
[REDACTED_AUTHOR]
 */
class NfcUnknownController(
    private val stateWidget: NfcUnknownWidget,
    private val vibrator: Vibrator,
    private val nfcManager: NfcManager,
    private val repeatOnFinish: Boolean = false
) {

    init {
        initTagDiscoveredCallback()
    }

    fun start() {
        stateWidget.setState(HowToState.Unknown.FindAntenna)
    }

    fun onFinish(callback: () -> Unit) {
        if (repeatOnFinish) {
        } else {
        }
    }

    private fun initTagDiscoveredCallback() {
        nfcManager.onTagDiscovered = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                //deprecated in API 26
                vibrator.vibrate(100)
            }
            postUI { stateWidget.setState(HowToState.Unknown.AntennaFound) }
        }
    }
}
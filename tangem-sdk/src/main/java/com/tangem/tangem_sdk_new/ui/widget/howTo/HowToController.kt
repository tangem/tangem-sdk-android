package com.tangem.tangem_sdk_new.ui.widget.howTo

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import com.tangem.tangem_sdk_new.nfc.NfcManager
import com.tangem.tangem_sdk_new.postUI
import com.tangem.tangem_sdk_new.ui.animation.VoidCallback

/**
[REDACTED_AUTHOR]
 */
class HowToController(
    private val stateWidget: NfcHowToWidget,
    private val vibrator: Vibrator,
    private val nfcManager: NfcManager
) {

    var onClose: VoidCallback?
        get() = stateWidget.onClose
        set(value) {
            stateWidget.onClose = value
        }

    fun getView(): View = stateWidget.view

    fun start() {
        nfcManager.readingIsActive = false
        stateWidget.setState(HowToState.Cancel)
        stateWidget.setState(HowToState.Init)
        stateWidget.setState(HowToState.Animate)
        stateWidget.onAnimationEnd = { start() }
        initTagDiscoveredCallback()
    }

    fun stop() {
        nfcManager.removeTagDiscoveredListener(tagDiscoveredListener)
        nfcManager.readingIsActive = true
        stateWidget.setState(HowToState.Cancel)
    }

    private fun initTagDiscoveredCallback() {
        nfcManager.addTagDiscoveredListener(tagDiscoveredListener)
    }

    private val tagDiscoveredListener = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(100)
        }
        postUI { stateWidget.setState(HowToState.AntennaFound) }
    }
}

enum class HowToMode {
    KNOWN, UNKNOWN
}

enum class HowToState {
    Init,
    Animate,
    AntennaFound,
    Cancel,
}
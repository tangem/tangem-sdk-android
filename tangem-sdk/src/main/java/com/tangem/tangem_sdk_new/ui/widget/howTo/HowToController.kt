package com.tangem.tangem_sdk_new.ui.widget.howTo

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.tangem.tangem_sdk_new.nfc.NfcManager
import com.tangem.tangem_sdk_new.postUI
import com.tangem.tangem_sdk_new.ui.animation.VoidCallback

/**
[REDACTED_AUTHOR]
 */
class HowToController(
    private val stateWidget: NfcHowToWidget,
    private val vibrator: Vibrator,
    private val nfcManager: NfcManager,
) : OkCallback {

    override var onOk: VoidCallback? = null
        set(value) {
            field = value
            stateWidget.onOk = {
                stateWidget.setState(HowToState.Cancel)
                value?.invoke()
            }
        }

    fun start() {
        stateWidget.setState(HowToState.Cancel)
        stateWidget.setState(HowToState.Init)
        stateWidget.setState(HowToState.Animate)
        stateWidget.onFinished = { start() }
        initTagDiscoveredCallback()
    }

    fun stop() {
        nfcManager.onTagDiscovered = null
    }

    private fun initTagDiscoveredCallback() {
        nfcManager.onTagDiscovered = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(100)
            }
            postUI { stateWidget.setState(HowToState.AntennaFound) }
        }
    }
}

sealed class HowToState {
    object Init : HowToState()
    object Animate : HowToState()
    object AntennaFound : HowToState()
    object Cancel : HowToState()
}
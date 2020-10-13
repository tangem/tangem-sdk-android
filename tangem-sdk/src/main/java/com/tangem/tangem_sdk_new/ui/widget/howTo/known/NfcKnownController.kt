package com.tangem.tangem_sdk_new.ui.widget.howTo.known

import com.tangem.tangem_sdk_new.postUI
import com.tangem.tangem_sdk_new.ui.widget.howTo.HowToState

/**
[REDACTED_AUTHOR]
 */
class NfcKnownController(
    private val stateWidget: NfcKnownWidget,
    private val repeatOnFinish: Boolean = false
) {

    init {
        if (repeatOnFinish) stateWidget.onFinished = { start() }
    }

    fun start() {
        stateWidget.setState(HowToState.Known.Prepare)
        postUI(3000) {
            stateWidget.setState(HowToState.Known.ShowNfcPosition)
            postUI(6000) {
                stateWidget.setState(HowToState.Known.TapToKnownPosition)
            }
        }
    }

    fun setOnFinishAnimationListener(callback: () -> Unit) {
        if (repeatOnFinish) {
            stateWidget.onFinished = {
                callback()
                start()
            }
        } else {
            stateWidget.onFinished = callback
        }
    }

}
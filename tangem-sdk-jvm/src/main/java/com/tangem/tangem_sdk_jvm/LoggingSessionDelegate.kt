package com.tangem.tangem_sdk_jvm

import com.tangem.*
import com.tangem.commands.PinType

class LoggingSessionDelegate : SessionViewDelegate {
    override fun dismiss() {
    }

    override fun onDelay(total: Int, current: Int, step: Int) {
        Log.session { "Performing long task: $current of $total" }
    }

    override fun onError(error: TangemError) {
        Log.error { "Error: ${error.code}, ${error.javaClass.simpleName}" }
    }

    override fun onSessionStopped(message: Message?) {
        Log.session { "Session completed" }
    }

    override fun onTagConnected() {
        Log.session { "Tag has been connected!" }
    }

    override fun onSessionStarted(cardId: String?, message: Message?, enableHowTo: Boolean) {
        Log.session { "Session started" }
    }

    override fun onPinRequested(pinType: PinType, isFirstAttempt: Boolean, callback: (pin: String) -> Unit) {
        if (isFirstAttempt) {
            Log.view { "TAG: Enter PIN:" }
        } else {
            Log.view { "TAG: Wrong Pin. Enter correct PIN:" }
        }

        val pin = readLine()
        pin?.let { callback.invoke(it) }
    }

    override fun onPinChangeRequested(pinType: PinType, callback: (pin: String) -> Unit) {
        Log.view { "TAG: Enter new PIN:" }
        val pin = readLine()
        pin?.let { callback.invoke(it) }
    }

    override fun onSecurityDelay(ms: Int, totalDurationSeconds: Int) {
        Log.warning { "Security delay: $ms" }
    }

    override fun onTagLost() {
        Log.warning { "Tag lost!" }
    }

    override fun onWrongCard(wrongValueType: WrongValueType) {
        Log.warning { "You tapped a different card. Please match the in-app Card ID to the your physical Card ID to continue this process." }
    }

    override fun setConfig(config: Config) {
    }

    override fun setMessage(message: Message?) {
    }

    companion object {
        const val TAG = "DELEGATE"
    }
}
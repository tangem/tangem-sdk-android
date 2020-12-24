package com.tangem.tangem_demo.ui.tasksLogger

import com.tangem.*
import com.tangem.commands.PinType

/**
[REDACTED_AUTHOR]
 */
enum class MessageType {
    VERBOSE,
    INFO,
    ERROR,
    VIEW_DELEGATE,
}

data class ConsoleMessage(val type: MessageType, val message: String)

interface ConsoleWriter {
    fun addLogMessage(message: ConsoleMessage)
}

class SdkLogger(private val consoleWriter: ConsoleWriter) : LoggerInterface {
    override fun i(logTag: String, message: String) {
        consoleWriter.addLogMessage(ConsoleMessage(MessageType.INFO, message))
    }

    override fun e(logTag: String, message: String) {
        consoleWriter.addLogMessage(ConsoleMessage(MessageType.ERROR, message))
    }

    override fun v(logTag: String, message: String) {
        consoleWriter.addLogMessage(ConsoleMessage(MessageType.VERBOSE, message))
    }
}

class EmptyViewDelegate(private val consoleWriter: ConsoleWriter) : SessionViewDelegate {

    override fun onSessionStarted(cardId: String?, message: Message?, enableHowTo: Boolean) {
//        consoleWriter.addLogMessage(MessageType.VIEW_DELEGATE, "onSessionStarted")
    }

    override fun onSecurityDelay(ms: Int, totalDurationSeconds: Int) {
        consoleWriter.addLogMessage(ConsoleMessage(MessageType.VIEW_DELEGATE, "SecDelay - ${totalDurationSeconds - ms}"))
    }

    override fun onDelay(total: Int, current: Int, step: Int) {
        consoleWriter.addLogMessage(ConsoleMessage(MessageType.VIEW_DELEGATE, "Delay - total: $total, current: $current, step: $step"))
    }

    override fun onTagLost() {
        consoleWriter.addLogMessage(ConsoleMessage(MessageType.ERROR, "onTagLost"))
    }

    override fun onTagConnected() {
        consoleWriter.addLogMessage(ConsoleMessage(MessageType.VIEW_DELEGATE, "onTagConnected"))
    }

    override fun onWrongCard(wrongValueType: WrongValueType) {
//        consoleWriter.addLogMessage(MessageType.VIEW_DELEGATE, "onWrongCard")
    }

    override fun onSessionStopped(message: Message?) {
//        consoleWriter.addLogMessage(MessageType.VIEW_DELEGATE, "onSessionStopped")
    }

    override fun onError(error: TangemError) {
        consoleWriter.addLogMessage(ConsoleMessage(MessageType.VIEW_DELEGATE, "onError"))
    }

    override fun onPinRequested(pinType: PinType, callback: (pin: String) -> Unit) {
        consoleWriter.addLogMessage(ConsoleMessage(MessageType.VIEW_DELEGATE, "onPinRequested"))
    }

    override fun onPinChangeRequested(pinType: PinType, callback: (pin: String) -> Unit) {
        consoleWriter.addLogMessage(ConsoleMessage(MessageType.VIEW_DELEGATE, "onPinChangeRequested"))
    }

    override fun setConfig(config: Config) {
//        consoleWriter.addLogMessage(MessageType.VIEW_DELEGATE, "setConfig")
    }

    override fun setMessage(message: Message?) {
//        consoleWriter.addLogMessage(MessageType.VIEW_DELEGATE, "setMessage")
    }

    override fun dismiss() {
//        consoleWriter.addLogMessage(MessageType.VIEW_DELEGATE, "dismiss")
    }
}
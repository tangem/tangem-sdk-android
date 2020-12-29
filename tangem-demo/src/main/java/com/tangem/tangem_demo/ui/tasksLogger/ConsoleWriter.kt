package com.tangem.tangem_demo.ui.tasksLogger

import com.tangem.LogMessage
import com.tangem.LoggerInterface
import com.tangem.MessageType

/**
[REDACTED_AUTHOR]
 */
interface ConsoleWriter {
    fun write(message: LogMessage)
}

class SdkLogger(private val consoleWriter: ConsoleWriter) : LoggerInterface {
    private var startTime: Long = 0
    private var prevMessageStartTime: Long = 0

    override fun i(logTag: String, message: String) {}
    override fun e(logTag: String, message: String) {}
    override fun v(logTag: String, message: String) {}

    override fun write(message: LogMessage) {
        if (message.type == MessageType.VERBOSE) return

        if (message.type == MessageType.STOP_SESSION) {
            if (startTime == 0L && prevMessageStartTime == 0L) {
                message.time = 0.toDouble()
                message.duration = 0.toDouble()
            } else {
                message.time = (message.initTime - startTime).toDouble()
                message.duration = (message.initTime - prevMessageStartTime).toDouble()
            }
            startTime = 0
            prevMessageStartTime = 0
        }
        if (startTime != 0L) {
            message.time = (message.initTime - startTime).toDouble()
            message.duration = (message.initTime - prevMessageStartTime).toDouble()
            prevMessageStartTime = message.initTime
        }
        if (message.type == MessageType.CONNECT) {
            startTime = message.initTime
            prevMessageStartTime = message.initTime
        }
        consoleWriter.write(message)
    }
}
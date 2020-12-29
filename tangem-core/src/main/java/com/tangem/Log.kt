package com.tangem

object Log {

    private val loggerList: MutableList<LoggerInterface> = mutableListOf()

    fun i(logTag: String, message: String) {
        loggerList.forEach { it.i(logTag, message) }
        write(TypedMessage(MessageType.INFO, (message)))
    }

    fun e(logTag: String, message: String) {
        loggerList.forEach { it.e(logTag, message) }
        write(TypedMessage(MessageType.ERROR, (message)))
    }

    fun v(logTag: String, message: String) {
        loggerList.forEach { it.v(logTag, message) }
        write(TypedMessage(MessageType.VERBOSE, message))
    }

    fun write(message: LogMessage) {
        loggerList.forEach { it.write(message) }
    }

    fun addLogger(logger: LoggerInterface) {
        loggerList.add(logger)
    }

    fun removeLogger(logger: LoggerInterface) {
        loggerList.remove(logger)
    }
}

/**
 * Interface for logging events within the SDK.
 *
 * It allows to use Android logger or to choose another.
 */
interface LoggerInterface {
    fun i(logTag: String, message: String)
    fun e(logTag: String, message: String)
    fun v(logTag: String, message: String)
    fun write(message: LogMessage)
}

enum class MessageType {
    START_SESSION,
    CONNECT,
    STOP_SESSION,
    VERBOSE,
    INFO,
    ERROR,
    SEND_DATA,
    RECEIVE_DATA,
    SEND_TLV,
    RECEIVE_TLV,
    SECURITY_DELAY,
    DELAY,
}

interface LogMessage {
    val type: MessageType
    val message: String
    val initTime: Long
    var time: Double
    var duration: Double
}

abstract class BaseLogMessage(
    override val type: MessageType,
    override val initTime: Long = System.nanoTime(),
    override var time: Double = 0.toDouble(),
    override var duration: Double = 0.toDouble()
) : LogMessage {
    override fun toString(): String {
        val name = if (message.isEmpty()) type.name else "${type.name}:"
        val prefix = "${time.toSeconds()}s (${duration.toSeconds()}s) $name"
        return "$prefix ${messageAtNewString()}"
    }

    private fun messageAtNewString(): String = when (type) {
        MessageType.SEND_TLV, MessageType.RECEIVE_TLV,
        MessageType.SEND_DATA, MessageType.RECEIVE_DATA -> "\n$message"
        else -> message
    }

    private fun Double.toSeconds(): String {
        return if (this == 0.toDouble()) "0"
        else String.format("%.3f", this / 1000000000)
    }
}

class TypedMessage(type: MessageType, override val message: String = "") : BaseLogMessage(type)

class DelayMessage(total: Int, current: Int, step: Int) : BaseLogMessage(MessageType.DELAY) {
    override val message: String = "total: $total, current: $current, step: $step"
}

class SecurityDelayMessage(remaining: Int, totalDurationSeconds: Int) : BaseLogMessage(MessageType.DELAY) {
    override val message: String = "remaining: $remaining, total: $totalDurationSeconds"
}
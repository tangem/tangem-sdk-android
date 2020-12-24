package com.tangem

object Log {

    private val loggerList: MutableList<LoggerInterface> = mutableListOf()
    private var singleLogger: LoggerInterface ? = null

    fun i(logTag: String, message: String) {
        loggerList.forEach { it.i(logTag, message) }
    }

    fun e(logTag: String, message: String) {
        loggerList.forEach { it.e(logTag, message) }
    }

    fun v(logTag: String, message: String) {
        loggerList.forEach { it.v(logTag, message) }
    }

    fun addLogger(logger: LoggerInterface) {
        loggerList.add(logger)
    }

    fun removeLogger(logger: LoggerInterface) {
        loggerList.remove(logger)
    }

    fun setLogger(logger: LoggerInterface){
        singleLogger?.let { loggerList.remove(it) }
        singleLogger = logger
        loggerList.add(logger)
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
}
package com.tangem.tangem_sdk_jvm

import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import com.tangem.Config
import com.tangem.Log
import com.tangem.TangemSdk
import com.tangem.TangemSdkLogger
import com.tangem.common.CardValuesDbStorage
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

fun TangemSdk.Companion.init(verbose: Boolean = false, indexOfTerminal: Int? = null): TangemSdk? {
    setLogger(verbose)
    val databaseDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    val reader = ReaderFactory().create(indexOfTerminal) ?: return null
    return TangemSdk(reader, LoggingSessionDelegate(), Config(),  CardValuesDbStorage(databaseDriver))
}

private fun setLogger(verbose: Boolean) {
    Log.addLogger(
            object : TangemSdkLogger {
                private val dateFormatter: DateFormat = SimpleDateFormat("HH:mm:ss:SSS", Locale.getDefault())
                private val tag = "Tangem SDK"

                override fun log(message: () -> String, level: Log.Level) {
                    val prefixDelimiter = if (level.prefix.isEmpty()) "" else ": "
                    val logMessage = "${dateFormatter.format(Date())}: ${level.prefix}$prefixDelimiter${message()}"

                    println("$tag: $logMessage")

//                    when (level) {
//                        Log.Level.Command -> TODO()
//                        Log.Level.Tlv -> TODO()
//                        Log.Level.Apdu -> TODO()
//                        Log.Level.Session -> TODO()
//                        Log.Level.Nfc -> TODO()
//                        Log.Level.Warning -> TODO()
//                        Log.Level.Error -> TODO()
//                        Log.Level.Debug -> TODO()
//                        Log.Level.Network -> TODO()
//                        Log.Level.View -> TODO()
//                    }
                }
            }
    )
}
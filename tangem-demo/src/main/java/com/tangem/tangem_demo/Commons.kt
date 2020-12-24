package com.tangem.tangem_demo

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.LayoutRes

/**
[REDACTED_AUTHOR]
 */
private val uiThread = Handler(Looper.getMainLooper())
private val workerThread = Handler(HandlerThread("DemoWorkerThread").apply { start() }.looper)

fun postUi(ms: Long = 0, func: Runnable) {
    if (ms == 0L) uiThread.post { func.run() } else uiThread.postDelayed(func, ms)
}

fun postWorker(ms: Long = 0, func: Runnable) {
    if (ms == 0L) workerThread.post { func.run() } else workerThread.postDelayed(func, ms)
}

fun <T> ViewGroup.inflate(@LayoutRes resId: Int, attach: Boolean = false): T =
    LayoutInflater.from(context).inflate(resId, this, attach) as T

fun String.splitCamelCase(): String {
    return replace(String.format("%s|%s|%s",
        "(?<=[A-Z])(?=[A-Z][a-z])",
        "(?<=[^A-Z])(?=[A-Z])",
        "(?<=[A-Za-z])(?=[^A-Za-z])"
    ).toRegex(), " ")
}
package com.tangem.sdk.url

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

internal class DefaultUrlOpener(private val context: Context) : UrlOpener {

    override fun openUrlExternalBrowser(url: String) {
        if (url.isEmpty()) return
        val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(browserIntent)
    }
}
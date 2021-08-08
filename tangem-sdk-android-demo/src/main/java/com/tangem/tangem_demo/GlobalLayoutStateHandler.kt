package com.tangem.tangem_demo

import android.view.View
import android.view.ViewTreeObserver

/**
[REDACTED_AUTHOR]
 */
class GlobalLayoutStateHandler<T : View>(
    private val view: T,
    attachImmediately: Boolean = true
) : ViewTreeObserver.OnGlobalLayoutListener {

    var onStateChanged: ((T) -> Unit)? = null

    private var isAttached: Boolean = false

    init {
        if (attachImmediately) attach()
    }

    fun attach() {
        if (isAttached) return

        isAttached = true
        view.viewTreeObserver.addOnGlobalLayoutListener(this)
    }

    fun detach() {
        view.viewTreeObserver.removeOnGlobalLayoutListener(this)
        isAttached = false
    }

    override fun onGlobalLayout() {
        onStateChanged?.invoke(view)
    }
}
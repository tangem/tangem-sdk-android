package com.tangem.json

import com.tangem.TangemError

/**
[REDACTED_AUTHOR]
 */
sealed class JsonError(final override val code: Int) : Exception(), TangemError {
    override var customMessage: String = code.toString()
    override val messageResId: Int? = null

    class NoSuchParamsError(override var customMessage: String) : JsonError(123123)
    class ParamsCastError(override var customMessage: String) : JsonError(321321)
}
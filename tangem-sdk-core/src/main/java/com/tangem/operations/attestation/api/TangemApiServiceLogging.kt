package com.tangem.operations.attestation.api

import okhttp3.Interceptor

object TangemApiServiceLogging {

    internal val apiInterceptors = mutableListOf<Interceptor>()

    fun addInterceptors(vararg interceptor: Interceptor) {
        apiInterceptors += interceptor
    }
}
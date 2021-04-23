package com.tangem.tester.common

import com.tangem.Config
import com.tangem.TangemSdk

interface TangemSdkFactory : TangemSdkHolder {
    fun create(config: Config): TangemSdk
}

interface TangemSdkHolder {
    fun getSdk(): TangemSdk
}
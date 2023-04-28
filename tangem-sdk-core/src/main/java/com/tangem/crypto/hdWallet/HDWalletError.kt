package com.tangem.crypto.hdWallet

/**
[REDACTED_AUTHOR]
 */
sealed class HDWalletError : Exception() {
    object HardenedNotSupported : HDWalletError()
    object DerivationFailed : HDWalletError()
    object WrongPath : HDWalletError()
    object WrongIndex : HDWalletError()
    object UnsupportedCurve : HDWalletError()
    object InvalidSeed : HDWalletError()

    override fun toString(): String = this::class.java.simpleName
}
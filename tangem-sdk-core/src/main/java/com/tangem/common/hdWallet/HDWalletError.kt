package com.tangem.common.hdWallet

/**
[REDACTED_AUTHOR]
 */
sealed class HDWalletError : Exception() {
    object HardenedNotSupported : HDWalletError()
    object DerivationFailed : HDWalletError()
    object WrongPath : HDWalletError()

    override fun toString(): String = this::class.java.simpleName
}
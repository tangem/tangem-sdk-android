package com.tangem.operations.attestation.api

internal enum class BaseUrl(val url: String) {
    CARD_DATA("https://api.tangem.org/"),
    VERIFY("https://verify.tangem.com/"),
}
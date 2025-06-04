package com.tangem.operations.attestation.api

internal enum class BaseUrl(val url: String) {
    CARD_DATA(url = "https://api.tangem.org/"),
    CARD_DATA_DEV(url = "https://api.tests-d.com/"),
    VERIFY(url = "https://verify.tangem.com/"),
}

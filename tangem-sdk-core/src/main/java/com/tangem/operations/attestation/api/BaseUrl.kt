package com.tangem.operations.attestation.api

internal enum class BaseUrl(val url: String) {
    CARD_DATA(url = "https://api.tangem.org/"),
    CARD_DATA_DEV(url = "[REDACTED_ENV_URL]"),
    VERIFY(url = "https://verify.tangem.com/"),
}
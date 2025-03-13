package com.tangem.operations.attestation.api.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class CardVerificationInfoResponse(
    @Json(name = "data_public_key_signature") val issuerSignature: String,
    @Json(name = "public_key_signature") val cardSignature: String,
)
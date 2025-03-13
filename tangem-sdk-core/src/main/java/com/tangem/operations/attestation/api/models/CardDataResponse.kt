package com.tangem.operations.attestation.api.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class CardDataResponse(
    @Json(name = "manufacturer_signature") val manufacturerSignature: String?,
    @Json(name = "issuer_signature") val issuerSignature: String?,
)
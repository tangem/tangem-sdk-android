package com.tangem.operations.attestation.api.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class CardArtworksResponse(
    @Json(name = "image_small_url") val imageSmallUrl: String?,
    @Json(name = "image_small_signature") val imageSmallSignature: String?,
    @Json(name = "image_large_url") val imageLargeUrl: String,
    @Json(name = "image_large_signature") val imageLargeSignature: String,
)
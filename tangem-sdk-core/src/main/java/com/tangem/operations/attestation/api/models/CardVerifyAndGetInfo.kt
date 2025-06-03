package com.tangem.operations.attestation.api.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

class CardVerifyAndGetInfo {

    @JsonClass(generateAdapter = true)
    data class Request(val requests: List<Item>) {

        @JsonClass(generateAdapter = true)
        data class Item(
            @Json(name = "CID") val cid: String,
            @Json(name = "publicKey") val publicKey: String,
        )
    }

    @JsonClass(generateAdapter = true)
    data class Response(val results: List<Item>? = null) {

        @JsonClass(generateAdapter = true)
        data class Item(
            @Json(name = "error") val error: String? = null,
            @Json(name = "CID") var cid: String = "",
            @Json(name = "passed") val passed: Boolean = false,
            @Json(name = "batch") val batch: String = "",
            @Json(name = "artwork") val artwork: ArtworkInfo? = null,
            @Json(name = "substitution") val substitution: SubstitutionInfo? = null,
        ) {

            @JsonClass(generateAdapter = true)
            data class SubstitutionInfo(
                @Json(name = "data") val data: String? = null,
                @Json(name = "signature") val signature: String? = null,
            )
        }
    }

    @JsonClass(generateAdapter = true)
    data class ArtworkInfo(
        val id: String = "",
        val hash: String = "",
        val date: String = "",
    )
}
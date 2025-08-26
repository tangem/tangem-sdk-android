package com.tangem.common

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class CardTokens constructor(
    val accessToken: ByteArray?,
    val identifyToken: ByteArray?,
)

package com.tangem.common.v8

import com.tangem.operations.securechannel.manageAccessTokens.ManageAccessTokensResponse

data class CardAccessTokens(
    val accessToken: ByteArray,
    val identifyToken: ByteArray,
) {

    constructor(manageAccessTokensResponse: ManageAccessTokensResponse) : this(
        manageAccessTokensResponse.accessToken,
        manageAccessTokensResponse.identifyToken
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CardAccessTokens

        if (!accessToken.contentEquals(other.accessToken)) return false
        if (!identifyToken.contentEquals(other.identifyToken)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = accessToken.contentHashCode()
        result = 31 * result + identifyToken.contentHashCode()
        return result
    }
}
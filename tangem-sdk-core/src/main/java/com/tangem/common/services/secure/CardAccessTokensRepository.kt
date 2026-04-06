package com.tangem.common.services.secure

import com.tangem.common.v8.CardAccessTokens

class CardAccessTokensRepository {

    fun save(tokens: CardAccessTokens, cardId: String) {

    }

    fun fetch(cardId: String): CardAccessTokens? {
        return null
    }

    fun contains(cardId: String): Boolean {
        return false
    }

    val isEmpty: Boolean
        get() = true

    fun deleteTokens(cardIds: Set<String>) {
        if (cardIds.isEmpty()) return

    }

    fun lock() {
    }
}
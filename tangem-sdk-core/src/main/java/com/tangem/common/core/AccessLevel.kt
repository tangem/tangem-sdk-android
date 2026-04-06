package com.tangem.common.core

enum class AccessLevel(val code: Int) {
    Public(0x01),
    PublicSecureChannel(0x02),
    User(0x04),
    Issuer(0x08),
    FileOwner(0x10),
    BackupCard(0x20);

    fun isPublic(): Boolean {
        return this == Public
    }

    fun isPublicSecureChannel(): Boolean {
        return this == PublicSecureChannel
    }

    companion object {
        private val values = values()
        fun byCode(code: Int): AccessLevel? = values.find { it.code == code }
    }
}
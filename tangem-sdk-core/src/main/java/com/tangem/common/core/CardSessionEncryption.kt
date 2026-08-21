package com.tangem.common.core

enum class CardSessionEncryption {

    /**
     * No encryption at all. Public access or custom encryption implemented by the command itself.
     * COS v8+ cards use this mode for some commands, but old cards use legacy encryption for all commands.
     */
    NONE,
    /**
     * COS v8+. Old cards use legacy encryption.
     */
    PUBLIC_SECURE_CHANNEL,
    /**
     * COS v8+. Old cards use legacy encryption. This mode is used for commands that require a PIN code.
     */
    SECURE_CHANNEL,
    /**
     * COS v8+. Old cards use legacy encryption.
     * This mode is used for commands the card accepts only after an explicit PIN authorization
     * (challenge/response), e.g. SET_PIN. A User channel established with access tokens is NOT
     * sufficient — the card rejects such commands with AccessDenied (intentional firmware
     * behavior: they require proving knowledge of the current access code).
     */
    SECURE_CHANNEL_WITH_PIN,
}
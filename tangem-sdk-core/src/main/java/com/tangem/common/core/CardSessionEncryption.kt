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
     * This mode is used for commands that require a PIN code and have a higher security level than the previous one.
     */
    SECURE_CHANNEL_WITH_PIN;
}
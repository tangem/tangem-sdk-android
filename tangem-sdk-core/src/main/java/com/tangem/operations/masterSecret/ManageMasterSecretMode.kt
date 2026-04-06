package com.tangem.operations.masterSecret

/**
 * Available modes for manage master secret
 */
enum class ManageMasterSecretMode(val rawValue: Int) {
    Create(rawValue = 0x00),
    Purge(rawValue = 0x01),
    ;

    companion object {
        private val values = values()
        fun byRawValue(rawValue: Int): ManageMasterSecretMode? = values.find { it.rawValue == rawValue }
    }
}
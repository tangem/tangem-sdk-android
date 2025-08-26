package com.tangem.operations.read

/**
[REDACTED_AUTHOR]
 *
 * Available modes for reading card information
 * Note: This modes available for cards with COS v. 4.0 and higher
 */
enum class ReadMode(val rawValue: Int) {
    Card(rawValue = 0x01),
    Wallet(rawValue = 0x02),
    WalletsList(rawValue = 0x03),
    MasterSecret(rawValue = 0x04)
    ;

    companion object {
        private val values = values()
        fun byRawValue(rawValue: Int): ReadMode? = values.find { it.rawValue == rawValue }
    }
}
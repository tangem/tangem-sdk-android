package com.tangem.commands.read

/**
[REDACTED_AUTHOR]
 *
 * Available modes for reading card information
 * Note: This modes available for cards with COS v.4.0 and higher
 */
enum class ReadMode(val rawValue: Int) {
    ReadCard(0x01),
    ReadWallet(0x02),
    ReadWalletList(0x03);

    companion object {
        private val values = values()
        fun byRawValue(rawValue: Int): ReadMode? = values.find { it.rawValue == rawValue }
    }
}
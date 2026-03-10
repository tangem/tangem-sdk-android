package com.tangem.sdk.extensions

import com.tangem.crypto.bip39.BIP39Wordlist
import com.tangem.crypto.bip39.Wordlist

fun Wordlist.Companion.getWordlist(): Wordlist {
    return BIP39Wordlist()
}
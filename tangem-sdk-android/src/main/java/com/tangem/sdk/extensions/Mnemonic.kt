package com.tangem.sdk.extensions

import com.tangem.crypto.bip39.DefaultMnemonic
import com.tangem.crypto.bip39.Mnemonic
import com.tangem.crypto.bip39.Wordlist

/**
 * Default init method for [Mnemonic].
 * @param mnemonic BIP39 seed phrase (mnemonic)
 */
fun Mnemonic.Companion.initDefault(mnemonic: String): Mnemonic {
    return DefaultMnemonic(mnemonic = mnemonic, wordlist = Wordlist.getWordlist())
}
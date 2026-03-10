package com.tangem.crypto.bip39

import org.junit.Assert.assertEquals
import org.junit.Test

class BIP39WordlistTest {

    // compatibility test
    @Test
    fun `BIP39Wordlist matches mnemonic_dictionary_en file`() {
        val bip39Words = BIP39Wordlist().words

        val wordsFromFile = javaClass.classLoader!!
            .getResourceAsStream("mnemonic/mnemonic_dictionary_en.txt")!!
            .bufferedReader()
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        assertEquals(
            "Number of words in BIP39Wordlist should match the file",
            wordsFromFile.size,
            bip39Words.size,
        )

        wordsFromFile.forEachIndexed { index, wordFromFile ->
            assertEquals(
                "Word at index $index should match",
                wordFromFile,
                bip39Words[index],
            )
        }
    }

    @Test
    fun `BIP39Wordlist contains exactly 2048 words`() {
        assertEquals(2048, BIP39Wordlist().words.size)
    }
}
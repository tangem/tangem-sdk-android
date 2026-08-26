package com.tangem.crypto

import com.tangem.common.card.EllipticCurve
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.extensions.toByteArray
import com.tangem.common.extensions.toHexString
import com.tangem.common.successOrNull
import com.tangem.crypto.bip39.BIP39Wordlist
import com.tangem.crypto.bip39.DefaultBIP39
import com.tangem.crypto.bip39.DefaultMnemonic
import com.tangem.crypto.bip39.Wordlist
import com.tangem.crypto.hdWallet.DerivationNode
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.crypto.hdWallet.HDWalletError
import com.tangem.crypto.hdWallet.bip32.BIP32
import com.tangem.crypto.hdWallet.bip32.ExtendedPrivateKey
import com.tangem.crypto.hdWallet.bip32.ExtendedPublicKey
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.assertFailsWith

// Tests for firmware 6.33
// / https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki#user-content-Test_Vectors
@Suppress("LargeClass")
internal class BIP32Test {

    init {
        CryptoUtils.initCrypto()
    }

    // BIP32: "In case parse256(IL) >= n or ki = 0, the resulting key is invalid, and one should proceed with the next value for i."
    // No real seed and path can hit this case on secp256k1 (the probability is lower than 1 in 2^127), so the digests are crafted:
    // IL = n, and IL = n - kpar of the test vector 1 master key - the only value that gives ki = 0 and Ki = the point at infinity.
    private val overflowedIL = "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141".hexToBytes()
    private val zeroChildIL = "170cd18dc2130bfae5105371d36c3639089aabae977af021ab3da57507f2d60c".hexToBytes()

    @Suppress("LongMethod")
    @Test
    fun testVector5() {
        // (invalid pubkey 020000000000000000000000000000000000000000000000000000000000000007)
        assertFailsWith<Exception> {
            ExtendedPublicKey.from(
                extendedKeyString = "xpub661MyMwAqRbcEYS8w7XLSVeEsBXy79zSzH1J8vCdxAZningWLdN3zgtU6Q5JXayek4PRsn35jii" +
                    "4veMimro1xefsM58PgBMrvdYre8QyULY",
                networkType = NetworkType.Mainnet,
            )
        }
        // (unknown extended key version)
        assertFailsWith<Exception> {
            ExtendedPublicKey.from(
                extendedKeyString = "DMwo58pR1QLEFihHiXPVykYB6fJmsTeHvyTp7hRThAtCX8CvYzgPcn8XnmdfHPmHJiEDXkTiJTVV9rH" +
                    "EBUem2mwVbbNfvT2MTcAqj3nesx8uBf9",
                networkType = NetworkType.Mainnet,
            )
        }

        // (unknown extended key version)
        assertFailsWith<Exception> {
            ExtendedPublicKey.from(
                extendedKeyString = "DMwo58pR1QLEFihHiXPVykYB6fJmsTeHvyTp7hRThAtCX8CvYzgPcn8XnmdfHGMQzT7ayAmfo4z3gY5" +
                    "KfbrZWZ6St24UVf2Qgo6oujFktLHdHY4",
                networkType = NetworkType.Mainnet,
            )
        }

        // (zero depth with non-zero index)
        assertFailsWith<Exception> {
            ExtendedPublicKey.from(
                extendedKeyString = "xpub661MyMwAuDcm6CRQ5N4qiHKrJ39Xe1R1NyfouMKTTWcguwVcfrZJaNvhpebzGerh7gucBvzEQWR" +
                    "ugZDuDXjNDRmXzSZe4c7mnTK97pTvGS8",
                networkType = NetworkType.Mainnet,
            )
        }

        // (zero depth with non-zero parent fingerprint)
        assertFailsWith<Exception> {
            ExtendedPublicKey.from(
                extendedKeyString = "xpub661no6RGEX3uJkY4bNnPcw4URcQTrSibUZ4NqJEw5eBkv7ovTwgiT91XX27VbEXGENhYRCf7hyE" +
                    "bWrR3FewATdCEebj6znwMfQkhRYHRLpJ",
                networkType = NetworkType.Mainnet,
            )
        }

        // (pubkey version / prvkey mismatch)
        assertFailsWith<Exception> {
            ExtendedPublicKey.from(
                extendedKeyString = "xpub661MyMwAqRbcEYS8w7XLSVeEsBXy79zSzH1J8vCdxAZningWLdN3zgtU6LBpB85b3D2yc8sfvZU" +
                    "521AAwdZafEz7mnzBBsz4wKY5fTtTQBm",
                networkType = NetworkType.Mainnet,
            )
        }

        // (prvkey version / pubkey mismatch)
        assertFailsWith<Exception> {
            ExtendedPrivateKey.from(
                extendedKeyString = "xprv9s21ZrQH143K24Mfq5zL5MhWK9hUhhGbd45hLXo2Pq2oqzMMo63oStZzFGTQQD3dC4H2D5GBj7v" +
                    "WvSQaaBv5cxi9gafk7NF3pnBju6dwKvH",
                networkType = NetworkType.Mainnet,
            )
        }

        // (invalid pubkey prefix 04)
        assertFailsWith<Exception> {
            ExtendedPublicKey.from(
                extendedKeyString = "xpub661MyMwAqRbcEYS8w7XLSVeEsBXy79zSzH1J8vCdxAZningWLdN3zgtU6Txnt3siSujt9RCVYsx" +
                    "4qHZGc62TG4McvMGcAUjeuwZdduYEvFn",
                networkType = NetworkType.Mainnet,
            )
        }

        // (invalid prvkey prefix 04)
        assertFailsWith<Exception> {
            ExtendedPrivateKey.from(
                extendedKeyString = "xprv9s21ZrQH143K24Mfq5zL5MhWK9hUhhGbd45hLXo2Pq2oqzMMo63oStZzFGpWnsj83BHtEy5Zt8C" +
                    "cDr1UiRXuWCmTQLxEK9vbz5gPstX92JQ",
                networkType = NetworkType.Mainnet,
            )
        }

        // (invalid pubkey prefix 01)
        assertFailsWith<Exception> {
            ExtendedPublicKey.from(
                extendedKeyString = "xpub661MyMwAqRbcEYS8w7XLSVeEsBXy79zSzH1J8vCdxAZningWLdN3zgtU6N8ZMMXctdiCjxTNq96" +
                    "4yKkwrkBJJwpzZS4HS2fxvyYUA4q2Xe4",
                networkType = NetworkType.Mainnet,
            )
        }

        // (invalid prvkey prefix 01)
        assertFailsWith<Exception> {
            ExtendedPrivateKey.from(
                extendedKeyString = "xprv9s21ZrQH143K24Mfq5zL5MhWK9hUhhGbd45hLXo2Pq2oqzMMo63oStZzFAzHGBP2UuGCqWLTAPL" +
                    "cMtD9y5gkZ6Eq3Rjuahrv17fEQ3Qen6J",
                networkType = NetworkType.Mainnet,
            )
        }

        // (zero depth with non-zero parent fingerprint)
        assertFailsWith<Exception> {
            ExtendedPrivateKey.from(
                extendedKeyString = "xprv9s2SPatNQ9Vc6GTbVMFPFo7jsaZySyzk7L8n2uqKXJen3KUmvQNTuLh3fhZMBoG3G4ZW1N2kZuH" +
                    "EPY53qmbZzCHshoQnNf4GvELZfqTUrcv",
                networkType = NetworkType.Mainnet,
            )
        }

        // (zero depth with non-zero index)
        assertFailsWith<Exception> {
            ExtendedPrivateKey.from(
                extendedKeyString = "xprv9s2SPatNQ9Vc9Umo8v8kmOT5XaBwKk2jMHhB9YGspgYh1qD9JTZT38pFZbtNkB7aBj6ZS3Hjag2" +
                    "SygmTPHDj3hHNuTzFQJfB9QgScSSisXT",
                networkType = NetworkType.Mainnet,
            )
        }

        // (pubkey version / prvkey mismatch)
        assertFailsWith<Exception> {
            ExtendedPrivateKey.from(
                extendedKeyString = "xprv9s2SPatNQ9VcFJnClsD1XebPGCXrGqhSvkGkDLJj8C3s5Efi6Fkvi6MhUroj91qtHt2pMjehHf4" +
                    "5yPPcA6xni5woQHVPUD9DkJxiD3TsFma",
                networkType = NetworkType.Mainnet,
            )
        }

        // (prvkey version / pubkey mismatch)
        assertFailsWith<Exception> {
            ExtendedPublicKey.from(
                extendedKeyString = "xpub661MyMwAqRbcEYS8w7XLSVeEsBXy79zSzH1J8vCdxAZningWLdN3zgtU6LBpB85b3D2yc8sfvZU" +
                    "521AAwdZafEz7mnzBBsz4wKY5fTtTQBm",
                networkType = NetworkType.Mainnet,
            )
        }

        // (invalid checksum)
        assertFailsWith<Exception> {
            ExtendedPrivateKey.from(
                extendedKeyString = "xprv9s21ZrQH143K3QTDL4LXw2F7HEK3wJUD2nW2nRk4stbPy6cq3jPPqjiChkVvvNKmPGJxWUtg6Ln" +
                    "F5kejMRNNU3TGtRBeJgk33yuGBxrMPHL",
                networkType = NetworkType.Mainnet,
            )
        }
    }

    @Test
    fun testSecp256k1MasterKeyGeneration() {
        val masterKey =
            BIP32.makeMasterKey("000102030405060708090a0b0c0d0e0f".hexToBytes(), EllipticCurve.Secp256k1)
        assertEquals(
            masterKey.privateKey.toHexString().lowercase(),
            "e8f32e723decf4051aefac8e2c93c9c5b214313817cdb01a1494b917c8436b35".lowercase(),
        )
        assertEquals(
            masterKey.chainCode.toHexString().lowercase(),
            "873dff81c02f525623fd1fe5167eac3a55a049de3d314bb42ee227ffed37d508".lowercase(),
        )

        val masterKey2 = BIP32.makeMasterKey(
            (
                "fffcf9f6f3f0edeae7e4e1dedbd8d5d2cfccc9c6c3c0bdbab7b4b1aeaba8a5a29f9c999693908d8a8784817e7b7875726f6" +
                    "c696663605d5a5754514e4b484542"
                ).hexToBytes(),
            EllipticCurve.Secp256k1,
        )
        assertEquals(
            masterKey2.privateKey.toHexString().lowercase(),
            "4b03d6fc340455b363f51020ad3ecca4f0850280cf436c70c727923f6db46c3e".lowercase(),
        )
        assertEquals(
            masterKey2.chainCode.toHexString().lowercase(),
            "60499f801b896d83179a4374aeb7822aaeaceaa0db1f85ee3e904c4defbd9689".lowercase(),
        )
    }

    @Test
    fun testSecp256r1MasterKeyGeneration() {
        val masterKey =
            BIP32.makeMasterKey("000102030405060708090a0b0c0d0e0f".hexToBytes(), EllipticCurve.Secp256r1)
        assertEquals(
            masterKey.privateKey.toHexString().lowercase(),
            "612091aaa12e22dd2abef664f8a01a82cae99ad7441b7ef8110424915c268bc2".lowercase(),
        )
        assertEquals(
            masterKey.chainCode.toHexString().lowercase(),
            "beeb672fe4621673f722f38529c07392fecaa61015c80c34f29ce8b41b3cb6ea".lowercase(),
        )

        val masterKey2 =
            BIP32.makeMasterKey(
                (
                    "fffcf9f6f3f0edeae7e4e1dedbd8d5d2cfccc9c6c3c0bdbab7b4b1aeaba8a5a29f9c999693908d8a8784817e7b78757" +
                        "26f6c696663605d5a5754514e4b484542"
                    ).hexToBytes(),
                EllipticCurve.Secp256r1,
            )
        assertEquals(
            masterKey2.privateKey.toHexString().lowercase(),
            "eaa31c2e46ca2962227cf21d73a7ef0ce8b31c756897521eb6c7b39796633357".lowercase(),
        )
        assertEquals(
            masterKey2.chainCode.toHexString().lowercase(),
            "96cd4465a9644e31528eda3592aa35eb39a9527769ce1855beafc1b81055e75d".lowercase(),
        )
    }

    @Test
    fun testEd25519MasterKeyGeneration() {
        val masterKey =
            BIP32.makeMasterKey("000102030405060708090a0b0c0d0e0f".hexToBytes(), EllipticCurve.Ed25519Slip0010)
        assertEquals(
            masterKey.privateKey.toHexString().lowercase(),
            "2b4be7f19ee27bbf30c667b642d5f4aa69fd169872f8fc3059c08ebae2eb19e7".lowercase(),
        )
        assertEquals(
            masterKey.chainCode.toHexString().lowercase(),
            "90046a93de5380a72b5e45010748567d5ea02bbf6522f979e05c0d8d8ca9fffb".lowercase(),
        )

        val masterKey2 =
            BIP32.makeMasterKey(
                (
                    "fffcf9f6f3f0edeae7e4e1dedbd8d5d2cfccc9c6c3c0bdbab7b4b1aeaba8a5a29f9c999693908d8a8784817e7b78757" +
                        "26f6c696663605d5a5754514e4b484542"
                    ).hexToBytes(),
                EllipticCurve.Ed25519Slip0010,
            )
        assertEquals(
            masterKey2.privateKey.toHexString().lowercase(),
            "171cb88b1b3c1db25add599712e36245d75bc65a1a5c9e18d76f9f2b1eab4012".lowercase(),
        )
        assertEquals(
            masterKey2.chainCode.toHexString().lowercase(),
            "ef70a74db9c3a5af931b5fe73ed8e1a53464133654fd55e7a66f8570b8e33c3b".lowercase(),
        )
    }

    @Test
    fun testSecp256r1MasterKeyGenerationRetry() {
        val masterKey = BIP32.makeMasterKey(
            "a7305bc8df8d0951f0cb224c0e95d7707cbdf2c6ce7e8d481fec69c7ff5e9446".hexToBytes(),
            EllipticCurve.Secp256r1,
        )
        assertEquals(
            masterKey.privateKey.toHexString().lowercase(),
            "3b8c18469a4634517d6d0b65448f8e6c62091b45540a1743c5846be55d47d88f".lowercase(),
        )
        assertEquals(
            masterKey.chainCode.toHexString().lowercase(),
            "7762f9729fed06121fd13f326884c82f59aa95c57ac492ce8c9654e60efd130c".lowercase(),
        )
    }

    @Test
    fun testMetaMaskTWCompatible() {
        val mnemonicPhrase = "scale wave venue cloth fruit empower afford one domain blouse romance artist"
        val mnemonic = DefaultMnemonic(mnemonicPhrase, createDefaultWordlist())
        val seed: ByteArray = mnemonic.generateSeed().successOrNull()!!
        assertEquals(
            seed.toHexString().lowercase(),
            (
                "d3eea633215dc4cb8ec2acd0d413adec1ebccb597ecf279886e584e9cb9ceb0788eb6f17a585acc12bc58fd586df6bbbdf3" +
                    "9af955656f24215cceab174344e62"
                ).lowercase(),
        )

        val extendedPrivateKey = BIP32.makeMasterKey(seed, EllipticCurve.Secp256k1)

        val pk = extendedPrivateKey.privateKey.toHexString().lowercase()
        assertEquals(pk, "589aeb596710f33d7ac31598ec10440a7df8808cf2c3d69ba670ff3fae66aafb".lowercase())

        assertEquals(
            extendedPrivateKey.serializeToWIFCompressed(NetworkType.Mainnet),
            "KzBwvPW6L5iwJSiE5vgS52Y69bUxfwizW3wF4C4Xa3ba3pdd7j63",
        )
    }

    @Test
    fun testMasterVector1() {
        val seed = "000102030405060708090a0b0c0d0e0f".hexToBytes()
        val mPriv = BIP32.makeMasterKey(seed, EllipticCurve.Secp256k1)
        val mPub = mPriv.makePublicKey(EllipticCurve.Secp256k1)

        val xpriv = mPriv.serialize(NetworkType.Mainnet)
        assertEquals(
            xpriv,
            "xprv9s21ZrQH143K3QTDL4LXw2F7HEK3wJUD2nW2nRk4stbPy6cq3jPPqjiChkVvvNKmPGJxWUtg6LnF5kejMRNNU3TGtRBe" +
                "Jgk33yuGBxrMPHi",
        )

        val xpub = mPub.serialize(NetworkType.Mainnet)
        assertEquals(
            xpub,
            "xpub661MyMwAqRbcFtXgS5sYJABqqG9YLmC4Q1Rdap9gSE8NqtwybGhePY2gZ29ESFjqJoCu1Rupje8YtGqsefD265TMg7us" +
                "UDFdp6W1EGMcet8",
        )
    }

    @Test
    fun testMasterVector2() {
        val seed =
            (
                "fffcf9f6f3f0edeae7e4e1dedbd8d5d2cfccc9c6c3c0bdbab7b4b1aeaba8a5a29f9c999693908d8a8784817e7b7875726f6" +
                    "c696663605d5a5754514e4b484542"
                ).hexToBytes()

        val mPriv = BIP32.makeMasterKey(seed, EllipticCurve.Secp256k1)
        val mPub = mPriv.makePublicKey(EllipticCurve.Secp256k1)

        val xpriv = mPriv.serialize(NetworkType.Mainnet)
        assertEquals(
            xpriv,
            "xprv9s21ZrQH143K31xYSDQpPDxsXRTUcvj2iNHm5NUtrGiGG5e2DtALGdso3pGz6ssrdK4PFmM8NSpSBHNqPqm55Qn3LqFt" +
                "T2emdEXVYsCzC2U",
        )

        val xpub = mPub.serialize(NetworkType.Mainnet)
        assertEquals(
            xpub,
            "xpub661MyMwAqRbcFW31YEwpkMuc5THy2PSt5bDMsktWQcFF8syAmRUapSCGu8ED9W6oDMSgv6Zz8idoc4a6mr8BDzTJY47L" +
                "JhkJ8UB7WEGuduB",
        )

        // Chain m/0
        val derivedPub = mPub.derivePublicKey(DerivationNode.NonHardened(0))
        val derivedXPub = derivedPub.serialize(NetworkType.Mainnet)
        assertEquals(
            derivedXPub,
            "xpub69H7F5d8KSRgmmdJg2KhpAK8SR3DjMwAdkxj3ZuxV27CprR9LgpeyGmXUbC6wb7ERfvrnKZjXoUmmDznezpbZb7ap6r1" +
                "D3tgFxHmwMkQTPH",
        )
    }

    @Test
    fun testMasterVector3() {
        val seed =
            (
                "4b381541583be4423346c643850da4b320e46a87ae3d2a4e6da11eba819cd4acba45d239319ac14f863b8d5ab5a0d0c64d2" +
                    "e8a1e7d1457df2e5a3c51c73235be"
                ).hexToBytes()

        val mPriv = BIP32.makeMasterKey(seed, EllipticCurve.Secp256k1)
        val mPub = mPriv.makePublicKey(EllipticCurve.Secp256k1)

        val xpriv = mPriv.serialize(NetworkType.Mainnet)
        assertEquals(
            xpriv,
            "xprv9s21ZrQH143K25QhxbucbDDuQ4naNntJRi4KUfWT7xo4EKsHt2QJDu7KXp1A3u7Bi1j8ph3EGsZ9Xvz9dGuVrtHHs7pX" +
                "eTzjuxBrCmmhgC6",
        )
        val xpub = mPub.serialize(NetworkType.Mainnet)
        assertEquals(
            xpub,
            "xpub661MyMwAqRbcEZVB4dScxMAdx6d4nFc9nvyvH3v4gJL378CSRZiYmhRoP7mBy6gSPSCYk6SzXPTf3ND1cZAceL7SfJ1Z" +
                "3GC8vBgp2epUt13",
        )
    }

    @Test
    fun testMasterVector4() {
        val seed = "3ddd5602285899a946114506157c7997e5444528f3003f6134712147db19b678".hexToBytes()

        val mPriv = BIP32.makeMasterKey(seed, EllipticCurve.Secp256k1)
        val mPub = mPriv.makePublicKey(EllipticCurve.Secp256k1)

        val xpriv = mPriv.serialize(NetworkType.Mainnet)
        assertEquals(
            xpriv,
            "xprv9s21ZrQH143K48vGoLGRPxgo2JNkJ3J3fqkirQC2zVdk5Dgd5w14S7fRDyHH4dWNHUgkvsvNDCkvAwcSHNAQwhwgNMgZ" +
                "hLtQC63zxwhQmRv",
        )

        val xpub = mPub.serialize(NetworkType.Mainnet)
        assertEquals(
            xpub,
            "xpub661MyMwAqRbcGczjuMoRm6dXaLDEhW1u34gKenbeYqAix21mdUKJyuyu5F1rzYGVxyL6tmgBUAEPrEz92mBXjByMRiJd" +
                "ba9wpnN37RLLAXa",
        )
    }

    // region: - Test that keys uploaded to a card are equal to locally computed
    @Test
    fun testKeyImportSecp256k1() {
        val seed = getSeed()
        val privKey = BIP32.makeMasterKey(seed, EllipticCurve.Secp256k1)
        val pubKey = privKey.makePublicKey(EllipticCurve.Secp256k1)

        val publicKeyFromCard = "03D902F35F560E0470C63313C7369168D9D7DF2D49BF295FD9FB7CB109CCEE0494"
        val chainCodeFromCard = "7923408DADD3C7B56EED15567707AE5E5DCA089DE972E07F3B860450E2A3B70E"
        assertEquals(pubKey.publicKey.toHexString(), publicKeyFromCard)
        assertEquals(privKey.chainCode.toHexString(), chainCodeFromCard)
    }

    @Test
    fun testKeyImportEd25519() {
        val seed = getSeed()
        val privKey = BIP32.makeMasterKey(seed, EllipticCurve.Ed25519Slip0010)
        val pubKey = privKey.makePublicKey(EllipticCurve.Ed25519Slip0010)

        val publicKeyFromCard = "E96B1C6B8769FDB0B34FBECFDF85C33B053CECAD9517E1AB88CBA614335775C1"
        val chainCodeFromCard = "DDFA71109701BBF7C126C8C7AB5880B0DEC3D167A8FE6AFA7A9597DF0BBEE72B"
        assertEquals(pubKey.publicKey.toHexString(), publicKeyFromCard)
        assertEquals(privKey.chainCode.toHexString(), chainCodeFromCard)
    }

    @Test
    fun testKeyImportSecp256r1() {
        val seed = getSeed()
        val privKey = BIP32.makeMasterKey(seed, EllipticCurve.Secp256r1)
        val pubKey = privKey.makePublicKey(EllipticCurve.Secp256r1)

        val publicKeyFromCard = "029983A77B155ED3B3B9E1DDD223BD5AA073834C8F61113B2F1B883AAA70971B5F"
        val chainCodeFromCard = "C7A888C4C670406E7AAEB6E86555CE0C4E738A337F9A9BC239F6D7E475110A4E"
        assertEquals(pubKey.publicKey.toHexString(), publicKeyFromCard)
        assertEquals(privKey.chainCode.toHexString(), chainCodeFromCard)
    }

    @Test
    fun testVector2Ed5519Slip0010() {
        val seed = (
            "fffcf9f6f3f0edeae7e4e1dedbd8d5d2cfccc9c6c3c0bdbab7b4b1aeaba8a5a29f9c999693908d8a8784817e7b78757" +
                "26f6c696663605d5a5754514e4b484542"
            ).hexToBytes()
        val masterKey = BIP32.makeMasterKey(seed, EllipticCurve.Ed25519Slip0010)

        assertEquals(
            masterKey.privateKey.toHexString(),
            "171cb88b1b3c1db25add599712e36245d75bc65a1a5c9e18d76f9f2b1eab4012".uppercase(),
        )
        assertEquals(
            masterKey.chainCode.toHexString(),
            "ef70a74db9c3a5af931b5fe73ed8e1a53464133654fd55e7a66f8570b8e33c3b".uppercase(),
        )
        assertEquals(
            masterKey.makePublicKey(EllipticCurve.Ed25519Slip0010).publicKey.toHexString(),
            "008fe9693f8fa62a4305a140b9764c5ee01e455963744fe18204b4fb948249308a".drop(2).uppercase(),
        )
    }

    @Test
    fun testVector2() {
        val seed = (
            "fffcf9f6f3f0edeae7e4e1dedbd8d5d2cfccc9c6c3c0bdbab7b4b1aeaba8a5a29f9c999693908d8a8784817e7b78757" +
                "26f6c696663605d5a5754514e4b484542"
            ).hexToBytes()

        val mPrv = BIP32.makeMasterKey(seed, curve = EllipticCurve.Secp256k1)
        val mPub = mPrv.makePublicKey(EllipticCurve.Secp256k1)

        val xPrv = mPrv.serialize(NetworkType.Mainnet)
        assertEquals(
            xPrv,
            "xprv9s21ZrQH143K31xYSDQpPDxsXRTUcvj2iNHm5NUtrGiGG5e2DtALGdso3pGz6ssrdK4PFmM8NSpSBHNqPqm55Qn3LqFt" +
                "T2emdEXVYsCzC2U",
        )

        val xPub = mPub.serialize(NetworkType.Mainnet)
        assertEquals(
            xPub,
            "xpub661MyMwAqRbcFW31YEwpkMuc5THy2PSt5bDMsktWQcFF8syAmRUapSCGu8ED9W6oDMSgv6Zz8idoc4a6mr8BDzTJY47L" +
                "JhkJ8UB7WEGuduB",
        )
    }

    @Test
    fun testVector3() {
        val seed = (
            "4b381541583be4423346c643850da4b320e46a87ae3d2a4e6da11eba819cd4acba45d239319ac14f863b8d5ab5a0d0c" +
                "64d2e8a1e7d1457df2e5a3c51c73235be"
            ).hexToBytes()

        val mPrv = BIP32.makeMasterKey(seed, curve = EllipticCurve.Secp256k1)
        val mPub = mPrv.makePublicKey(EllipticCurve.Secp256k1)

        val xPrv = mPrv.serialize(NetworkType.Mainnet)
        assertEquals(
            xPrv,
            "xprv9s21ZrQH143K25QhxbucbDDuQ4naNntJRi4KUfWT7xo4EKsHt2QJDu7KXp1A3u7Bi1j8ph3EGsZ9Xvz9dGuVrtHHs7pX" +
                "eTzjuxBrCmmhgC6",
        )

        val xPub = mPub.serialize(NetworkType.Mainnet)
        assertEquals(
            xPub,
            "xpub661MyMwAqRbcEZVB4dScxMAdx6d4nFc9nvyvH3v4gJL378CSRZiYmhRoP7mBy6gSPSCYk6SzXPTf3ND1cZAceL7SfJ1Z" +
                "3GC8vBgp2epUt13",
        )
    }

    // region: - Private child key derivation (CKDpriv), BIP32 test vectors 1-4

    @Test
    fun testPrivateDerivationVector1() {
        val seed = "000102030405060708090a0b0c0d0e0f".hexToBytes()
        val masterKey = BIP32.makeMasterKey(seed, EllipticCurve.Secp256k1)

        // Chain m
        assertExtendedKeys(
            masterKey,
            xprv = "xprv9s21ZrQH143K3QTDL4LXw2F7HEK3wJUD2nW2nRk4stbPy6cq3jPPqjiChkVvvNKmPGJxWUtg6LnF5kejMRNNU3TGtRBeJgk33yuGBxrMPHi",
            xpub = "xpub661MyMwAqRbcFtXgS5sYJABqqG9YLmC4Q1Rdap9gSE8NqtwybGhePY2gZ29ESFjqJoCu1Rupje8YtGqsefD265TMg7usUDFdp6W1EGMcet8",
        )

        // Chain m/0H
        var derivedKey = masterKey.derivePrivateKey(DerivationNode.Hardened(0))
        assertExtendedKeys(
            derivedKey,
            xprv = "xprv9uHRZZhk6KAJC1avXpDAp4MDc3sQKNxDiPvvkX8Br5ngLNv1TxvUxt4cV1rGL5hj6KCesnDYUhd7oWgT11eZG7XnxHrnYeSvkzY7d2bhkJ7",
            xpub = "xpub68Gmy5EdvgibQVfPdqkBBCHxA5htiqg55crXYuXoQRKfDBFA1WEjWgP6LHhwBZeNK1VTsfTFUHCdrfp1bgwQ9xv5ski8PX9rL2dZXvgGDnw",
        )

        // Chain m/0H/1
        derivedKey = derivedKey.derivePrivateKey(DerivationNode.NonHardened(1))
        assertExtendedKeys(
            derivedKey,
            xprv = "xprv9wTYmMFdV23N2TdNG573QoEsfRrWKQgWeibmLntzniatZvR9BmLnvSxqu53Kw1UmYPxLgboyZQaXwTCg8MSY3H2EU4pWcQDnRnrVA1xe8fs",
            xpub = "xpub6ASuArnXKPbfEwhqN6e3mwBcDTgzisQN1wXN9BJcM47sSikHjJf3UFHKkNAWbWMiGj7Wf5uMash7SyYq527Hqck2AxYysAA7xmALppuCkwQ",
        )

        // Chain m/0H/1/2H
        derivedKey = derivedKey.derivePrivateKey(DerivationNode.Hardened(2))
        assertExtendedKeys(
            derivedKey,
            xprv = "xprv9z4pot5VBttmtdRTWfWQmoH1taj2axGVzFqSb8C9xaxKymcFzXBDptWmT7FwuEzG3ryjH4ktypQSAewRiNMjANTtpgP4mLTj34bhnZX7UiM",
            xpub = "xpub6D4BDPcP2GT577Vvch3R8wDkScZWzQzMMUm3PWbmWvVJrZwQY4VUNgqFJPMM3No2dFDFGTsxxpG5uJh7n7epu4trkrX7x7DogT5Uv6fcLW5",
        )

        // Chain m/0H/1/2H/2
        derivedKey = derivedKey.derivePrivateKey(DerivationNode.NonHardened(2))
        assertExtendedKeys(
            derivedKey,
            xprv = "xprvA2JDeKCSNNZky6uBCviVfJSKyQ1mDYahRjijr5idH2WwLsEd4Hsb2Tyh8RfQMuPh7f7RtyzTtdrbdqqsunu5Mm3wDvUAKRHSC34sJ7in334",
            xpub = "xpub6FHa3pjLCk84BayeJxFW2SP4XRrFd1JYnxeLeU8EqN3vDfZmbqBqaGJAyiLjTAwm6ZLRQUMv1ZACTj37sR62cfN7fe5JnJ7dh8zL4fiyLHV",
        )

        // Chain m/0H/1/2H/2/1000000000
        derivedKey = derivedKey.derivePrivateKey(DerivationNode.NonHardened(1000000000))
        assertExtendedKeys(
            derivedKey,
            xprv = "xprvA41z7zogVVwxVSgdKUHDy1SKmdb533PjDz7J6N6mV6uS3ze1ai8FHa8kmHScGpWmj4WggLyQjgPie1rFSruoUihUZREPSL39UNdE3BBDu76",
            xpub = "xpub6H1LXWLaKsWFhvm6RVpEL9P4KfRZSW7abD2ttkWP3SSQvnyA8FSVqNTEcYFgJS2UaFcxupHiYkro49S8yGasTvXEYBVPamhGW6cFJodrTHy",
        )

        val derivedByPath = masterKey.derivePrivateKey(DerivationPath("m/0'/1/2'/2/1000000000"))
        assertEquals(derivedKey, derivedByPath)
    }

    @Test
    fun testPrivateDerivationVector2() {
        val seed = "fffcf9f6f3f0edeae7e4e1dedbd8d5d2cfccc9c6c3c0bdbab7b4b1aeaba8a5a29f9c999693908d8a8784817e7b7875726f6c696663605d5a5754514e4b484542".hexToBytes()
        val masterKey = BIP32.makeMasterKey(seed, EllipticCurve.Secp256k1)

        // Chain m
        assertExtendedKeys(
            masterKey,
            xprv = "xprv9s21ZrQH143K31xYSDQpPDxsXRTUcvj2iNHm5NUtrGiGG5e2DtALGdso3pGz6ssrdK4PFmM8NSpSBHNqPqm55Qn3LqFtT2emdEXVYsCzC2U",
            xpub = "xpub661MyMwAqRbcFW31YEwpkMuc5THy2PSt5bDMsktWQcFF8syAmRUapSCGu8ED9W6oDMSgv6Zz8idoc4a6mr8BDzTJY47LJhkJ8UB7WEGuduB",
        )

        // Chain m/0
        var derivedKey = masterKey.derivePrivateKey(DerivationNode.NonHardened(0))
        assertExtendedKeys(
            derivedKey,
            xprv = "xprv9vHkqa6EV4sPZHYqZznhT2NPtPCjKuDKGY38FBWLvgaDx45zo9WQRUT3dKYnjwih2yJD9mkrocEZXo1ex8G81dwSM1fwqWpWkeS3v86pgKt",
            xpub = "xpub69H7F5d8KSRgmmdJg2KhpAK8SR3DjMwAdkxj3ZuxV27CprR9LgpeyGmXUbC6wb7ERfvrnKZjXoUmmDznezpbZb7ap6r1D3tgFxHmwMkQTPH",
        )

        // Chain m/0/2147483647H
        derivedKey = derivedKey.derivePrivateKey(DerivationNode.Hardened(2147483647))
        assertExtendedKeys(
            derivedKey,
            xprv = "xprv9wSp6B7kry3Vj9m1zSnLvN3xH8RdsPP1Mh7fAaR7aRLcQMKTR2vidYEeEg2mUCTAwCd6vnxVrcjfy2kRgVsFawNzmjuHc2YmYRmagcEPdU9",
            xpub = "xpub6ASAVgeehLbnwdqV6UKMHVzgqAG8Gr6riv3Fxxpj8ksbH9ebxaEyBLZ85ySDhKiLDBrQSARLq1uNRts8RuJiHjaDMBU4Zn9h8LZNnBC5y4a",
        )

        // Chain m/0/2147483647H/1
        derivedKey = derivedKey.derivePrivateKey(DerivationNode.NonHardened(1))
        assertExtendedKeys(
            derivedKey,
            xprv = "xprv9zFnWC6h2cLgpmSA46vutJzBcfJ8yaJGg8cX1e5StJh45BBciYTRXSd25UEPVuesF9yog62tGAQtHjXajPPdbRCHuWS6T8XA2ECKADdw4Ef",
            xpub = "xpub6DF8uhdarytz3FWdA8TvFSvvAh8dP3283MY7p2V4SeE2wyWmG5mg5EwVvmdMVCQcoNJxGoWaU9DCWh89LojfZ537wTfunKau47EL2dhHKon",
        )

        // Chain m/0/2147483647H/1/2147483646H
        derivedKey = derivedKey.derivePrivateKey(DerivationNode.Hardened(2147483646))
        assertExtendedKeys(
            derivedKey,
            xprv = "xprvA1RpRA33e1JQ7ifknakTFpgNXPmW2YvmhqLQYMmrj4xJXXWYpDPS3xz7iAxn8L39njGVyuoseXzU6rcxFLJ8HFsTjSyQbLYnMpCqE2VbFWc",
            xpub = "xpub6ERApfZwUNrhLCkDtcHTcxd75RbzS1ed54G1LkBUHQVHQKqhMkhgbmJbZRkrgZw4koxb5JaHWkY4ALHY2grBGRjaDMzQLcgJvLJuZZvRcEL",
        )

        // Chain m/0/2147483647H/1/2147483646H/2
        derivedKey = derivedKey.derivePrivateKey(DerivationNode.NonHardened(2))
        assertExtendedKeys(
            derivedKey,
            xprv = "xprvA2nrNbFZABcdryreWet9Ea4LvTJcGsqrMzxHx98MMrotbir7yrKCEXw7nadnHM8Dq38EGfSh6dqA9QWTyefMLEcBYJUuekgW4BYPJcr9E7j",
            xpub = "xpub6FnCn6nSzZAw5Tw7cgR9bi15UV96gLZhjDstkXXxvCLsUXBGXPdSnLFbdpq8p9HmGsApME5hQTZ3emM2rnY5agb9rXpVGyy3bdW6EEgAtqt",
        )

        val derivedByPath = masterKey.derivePrivateKey(DerivationPath("m/0/2147483647'/1/2147483646'/2"))
        assertEquals(derivedKey, derivedByPath)
    }

    @Test
    fun testPrivateDerivationVector3() {
        val seed = "4b381541583be4423346c643850da4b320e46a87ae3d2a4e6da11eba819cd4acba45d239319ac14f863b8d5ab5a0d0c64d2e8a1e7d1457df2e5a3c51c73235be".hexToBytes()
        val masterKey = BIP32.makeMasterKey(seed, EllipticCurve.Secp256k1)

        // Chain m
        assertExtendedKeys(
            masterKey,
            xprv = "xprv9s21ZrQH143K25QhxbucbDDuQ4naNntJRi4KUfWT7xo4EKsHt2QJDu7KXp1A3u7Bi1j8ph3EGsZ9Xvz9dGuVrtHHs7pXeTzjuxBrCmmhgC6",
            xpub = "xpub661MyMwAqRbcEZVB4dScxMAdx6d4nFc9nvyvH3v4gJL378CSRZiYmhRoP7mBy6gSPSCYk6SzXPTf3ND1cZAceL7SfJ1Z3GC8vBgp2epUt13",
        )

        // Chain m/0H
        val derivedKey = masterKey.derivePrivateKey(DerivationNode.Hardened(0))
        assertExtendedKeys(
            derivedKey,
            xprv = "xprv9uPDJpEQgRQfDcW7BkF7eTya6RPxXeJCqCJGHuCJ4GiRVLzkTXBAJMu2qaMWPrS7AANYqdq6vcBcBUdJCVVFceUvJFjaPdGZ2y9WACViL4L",
            xpub = "xpub68NZiKmJWnxxS6aaHmn81bvJeTESw724CRDs6HbuccFQN9Ku14VQrADWgqbhhTHBaohPX4CjNLf9fq9MYo6oDaPPLPxSb7gwQN3ih19Zm4Y",
        )

        val derivedByPath = masterKey.derivePrivateKey(DerivationPath("m/0'"))
        assertEquals(derivedKey, derivedByPath)
    }

    @Test
    fun testPrivateDerivationVector4() {
        val seed = "3ddd5602285899a946114506157c7997e5444528f3003f6134712147db19b678".hexToBytes()
        val masterKey = BIP32.makeMasterKey(seed, EllipticCurve.Secp256k1)

        // Chain m
        assertExtendedKeys(
            masterKey,
            xprv = "xprv9s21ZrQH143K48vGoLGRPxgo2JNkJ3J3fqkirQC2zVdk5Dgd5w14S7fRDyHH4dWNHUgkvsvNDCkvAwcSHNAQwhwgNMgZhLtQC63zxwhQmRv",
            xpub = "xpub661MyMwAqRbcGczjuMoRm6dXaLDEhW1u34gKenbeYqAix21mdUKJyuyu5F1rzYGVxyL6tmgBUAEPrEz92mBXjByMRiJdba9wpnN37RLLAXa",
        )

        // Chain m/0H
        var derivedKey = masterKey.derivePrivateKey(DerivationNode.Hardened(0))
        assertExtendedKeys(
            derivedKey,
            xprv = "xprv9vB7xEWwNp9kh1wQRfCCQMnZUEG21LpbR9NPCNN1dwhiZkjjeGRnaALmPXCX7SgjFTiCTT6bXes17boXtjq3xLpcDjzEuGLQBM5ohqkao9G",
            xpub = "xpub69AUMk3qDBi3uW1sXgjCmVjJ2G6WQoYSnNHyzkmdCHEhSZ4tBok37xfFEqHd2AddP56Tqp4o56AePAgCjYdvpW2PU2jbUPFKsav5ut6Ch1m",
        )

        // Chain m/0H/1H
        derivedKey = derivedKey.derivePrivateKey(DerivationNode.Hardened(1))
        assertExtendedKeys(
            derivedKey,
            xprv = "xprv9xJocDuwtYCMNAo3Zw76WENQeAS6WGXQ55RCy7tDJ8oALr4FWkuVoHJeHVAcAqiZLE7Je3vZJHxspZdFHfnBEjHqU5hG1Jaj32dVoS6XLT1",
            xpub = "xpub6BJA1jSqiukeaesWfxe6sNK9CCGaujFFSJLomWHprUL9DePQ4JDkM5d88n49sMGJxrhpjazuXYWdMf17C9T5XnxkopaeS7jGk1GyyVziaMt",
        )

        val derivedByPath = masterKey.derivePrivateKey(DerivationPath("m/0'/1'"))
        assertEquals(derivedKey, derivedByPath)
    }
    // endregion

    // region: - Derivation retry on invalid child key

    @Test
    fun testInvalidPrivateChildKeyDetection() {
        val masterKey = BIP32.makeMasterKey("000102030405060708090a0b0c0d0e0f".hexToBytes(), EllipticCurve.Secp256k1)
        val parentPublicKey = masterKey.makePublicKey(EllipticCurve.Secp256k1).publicKey

        for (il in listOf(overflowedIL, zeroChildIL)) {
            assertFailsWith<HDWalletError.InvalidChildKey> {
                masterKey.makeChildKey(digest = il + masterKey.chainCode, index = 0, parentPublicKey = parentPublicKey)
            }
        }
    }

    @Test
    fun testInvalidPublicChildKeyDetection() {
        val masterKey = ExtendedPublicKey(
            publicKey = "0339a36013301597daef41fbe593a02cc513d0b55527ec2df1050e2e8ff49c85c2".hexToBytes(),
            chainCode = "873dff81c02f525623fd1fe5167eac3a55a049de3d314bb42ee227ffed37d508".hexToBytes(),
        )

        for (il in listOf(overflowedIL, zeroChildIL)) {
            assertFailsWith<HDWalletError.InvalidChildKey> {
                masterKey.makeChildKey(digest = il + masterKey.chainCode, index = 0)
            }
        }
    }

    @Test
    fun testPrivateKeyDerivationProceedsWithTheNextIndex() {
        val masterKey = BIP32.makeMasterKey("000102030405060708090a0b0c0d0e0f".hexToBytes(), EllipticCurve.Secp256k1)
        val parentPublicKey = masterKey.makePublicKey(EllipticCurve.Secp256k1).publicKey

        val derivedKey = BIP32.deriveWithRetry(0) { index ->
            val digest = if (index == 0L) {
                zeroChildIL + masterKey.chainCode
            } else {
                masterKey.chainCode.hmacSha512(parentPublicKey + index.toByteArray(4))
            }
            masterKey.makeChildKey(digest = digest, index = index, parentPublicKey = parentPublicKey)
        }

        assertEquals(masterKey.derivePrivateKey(DerivationNode.NonHardened(1)), derivedKey)
        assertEquals(1L, derivedKey.childNumber)
    }

    @Test
    fun testPublicKeyDerivationProceedsWithTheNextIndex() {
        val masterKey = ExtendedPublicKey(
            publicKey = "0339a36013301597daef41fbe593a02cc513d0b55527ec2df1050e2e8ff49c85c2".hexToBytes(),
            chainCode = "873dff81c02f525623fd1fe5167eac3a55a049de3d314bb42ee227ffed37d508".hexToBytes(),
        )

        val derivedKey = BIP32.deriveWithRetry(0) { index ->
            val digest = if (index == 0L) {
                zeroChildIL + masterKey.chainCode
            } else {
                masterKey.chainCode.hmacSha512(masterKey.publicKey + index.toByteArray(4))
            }
            masterKey.makeChildKey(digest = digest, index = index)
        }

        assertEquals(masterKey.derivePublicKey(DerivationNode.NonHardened(1)), derivedKey)
        assertEquals(1L, derivedKey.childNumber)
    }

    @Test
    fun testDeriveWithRetryPropagatesOtherErrors() {
        var attempts = 0

        assertFailsWith<HDWalletError.HardenedNotSupported> {
            BIP32.deriveWithRetry<Unit>(0) {
                attempts += 1
                throw HDWalletError.HardenedNotSupported
            }
        }

        assertEquals(1, attempts)
    }

    @Test
    fun testDeriveWithRetryFailsOnIndexOverflow() {
        val attemptedIndexes = mutableListOf<Long>()

        assertFailsWith<HDWalletError.InvalidChildKey> {
            BIP32.deriveWithRetry<Unit>(BIP32.Constants.maxIndex - 1) { index ->
                attemptedIndexes.add(index)
                throw HDWalletError.InvalidChildKey
            }
        }

        assertEquals(listOf(BIP32.Constants.maxIndex - 1, BIP32.Constants.maxIndex), attemptedIndexes)
    }
    // endregion

    // region: - Hardened index semantics

    // BIP32 defines hardened derivation solely by i >= 2^31, regardless of how the node was constructed.
    @Test
    fun testNonHardenedNodeWithHardenedIndex() {
        val masterKey = BIP32.makeMasterKey("000102030405060708090a0b0c0d0e0f".hexToBytes(), EllipticCurve.Secp256k1)

        assertEquals(
            masterKey.derivePrivateKey(DerivationNode.Hardened(0)),
            masterKey.derivePrivateKey(DerivationNode.NonHardened(2147483648)),
        )

        assertFailsWith<HDWalletError.HardenedNotSupported> {
            masterKey.makePublicKey(EllipticCurve.Secp256k1).derivePublicKey(DerivationNode.NonHardened(2147483648))
        }
    }

    @Test
    fun testPrivateDerivationMatchesPublicDerivation() {
        val path = DerivationPath("m/0/1")
        val masterKey = ExtendedPrivateKey.from(
            "xprv9s21ZrQH143K31xYSDQpPDxsXRTUcvj2iNHm5NUtrGiGG5e2DtALGdso3pGz6ssrdK4PFmM8NSpSBHNqPqm55Qn3LqFtT2emdEXVYsCzC2U",
            NetworkType.Mainnet,
        ) as ExtendedPrivateKey

        val publicFromPrivate = masterKey.derivePrivateKey(path).makePublicKey(EllipticCurve.Secp256k1)
        val publicFromPublic = masterKey.makePublicKey(EllipticCurve.Secp256k1).derivePublicKey(path)

        assertEquals(publicFromPrivate, publicFromPublic)
    }
    // endregion

    private fun assertExtendedKeys(key: ExtendedPrivateKey, xprv: String, xpub: String) {
        assertEquals(xprv, key.serialize(NetworkType.Mainnet))
        assertEquals(xpub, key.makePublicKey(EllipticCurve.Secp256k1).serialize(NetworkType.Mainnet))
    }

    private fun getSeed(): ByteArray {
        val mnemonicString = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
            "abandon about"
        val bip39 = createDefaultBIP39()
        val parsedMnemonic = bip39.parse(mnemonicString)
        return bip39.generateSeed(parsedMnemonic).successOrNull()!!
    }
    // endregion

    private fun createDefaultWordlist(): Wordlist = BIP39Wordlist()

    private fun createDefaultBIP39(): DefaultBIP39 = DefaultBIP39(createDefaultWordlist())
}
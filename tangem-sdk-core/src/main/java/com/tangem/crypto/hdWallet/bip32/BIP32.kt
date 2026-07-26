package com.tangem.crypto.hdWallet.bip32

import com.tangem.common.card.EllipticCurve
import com.tangem.common.core.TangemSdkError
import com.tangem.crypto.CryptoUtils
import com.tangem.crypto.hdWallet.HDWalletError
import com.tangem.crypto.hmacSha512

/**
[REDACTED_AUTHOR]
 */
object BIP32 {

    @Suppress("MagicNumber")
    @Throws(Exception::class)
    fun makeMasterKey(seed: ByteArray, curve: EllipticCurve): ExtendedPrivateKey {
        val hmacKey = curve.hmacKey ?: throw TangemSdkError.UnsupportedCurve()

        // The seed must be between 128 and 512 bits
        if (seed.size !in 16..64) {
            throw HDWalletError.InvalidSeed
        }

        val keyData = hmacKey.rawValue.toByteArray(Charsets.UTF_8)
        val authenticationCode = keyData.hmacSha512(seed)
        val i = authenticationCode
        val iL = i.copyOfRange(0, 32)
        val iR = i.copyOfRange(32, 64)

        // Verify the key
        // https://github.com/satoshilabs/slips/blob/master/slip-0010.md
        if (curve != EllipticCurve.Ed25519Slip0010 && !CryptoUtils.isPrivateKeyValid(iL, curve)) {
            return makeMasterKey(i, curve)
        }

        return ExtendedPrivateKey(privateKey = iL, chainCode = iR)
    }

    /**
     * BIP32: in case the derived child key is invalid, one should proceed with the next value for i.
     */
    @Throws(HDWalletError::class)
    fun <Key> deriveWithRetry(index: Long, makeChildKey: (Long) -> Key): Key {
        var index = index

        while (true) {
            try {
                return makeChildKey(index)
            } catch (e: HDWalletError.InvalidChildKey) {
                if (index >= Constants.maxIndex) throw e

                index += 1
            }
        }
    }

    enum class HMACKey(val rawValue: String) {
        Secp256k1("Bitcoin seed"),
        Secp256r1("Nist256p1 seed"),
        Ed25519("ed25519 seed"),
    }

    object Constants {
        const val hardenedOffset: Long = 2147483648

        /** The maximum derivation index (UInt32.max), used as the retry upper bound. */
        const val maxIndex: Long = 4294967295
        const val hardenedSymbol: String = "'"
        const val alternativeHardenedSymbol: String = "’"
        const val masterKeySymbol: String = "m"
        const val separatorSymbol: String = "/"
    }
}

private val EllipticCurve.hmacKey: BIP32.HMACKey?
    get() = when (this) {
        EllipticCurve.Secp256k1, EllipticCurve.Bip0340 -> BIP32.HMACKey.Secp256k1
        EllipticCurve.Ed25519Slip0010 -> BIP32.HMACKey.Ed25519
        EllipticCurve.Secp256r1 -> BIP32.HMACKey.Secp256r1
        EllipticCurve.Bls12381G2, EllipticCurve.Bls12381G2Aug, EllipticCurve.Bls12381G2Pop -> {
            // https://eips.ethereum.org/EIPS/eip-2333#derive_master_sk
            null
        }
        EllipticCurve.Ed25519 -> {
            // we use Ikarus master key generation scheme for this curve
            // https://github.com/satoshilabs/slips/blob/master/slip-0023.md
            null
        }
    }
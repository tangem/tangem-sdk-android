package com.tangem.crypto.hdWallet.bip32

import com.squareup.moshi.JsonClass
import com.tangem.Log
import com.tangem.common.card.EllipticCurve
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.calculateRipemd160
import com.tangem.common.extensions.calculateSha256
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.extensions.toByteArray
import com.tangem.common.extensions.toInt
import com.tangem.common.extensions.toLong
import com.tangem.crypto.CryptoUtils
import com.tangem.crypto.NetworkType
import com.tangem.crypto.Secp256k1
import com.tangem.crypto.WIF
import com.tangem.crypto.decodeBase58WithChecksum
import com.tangem.crypto.encodeToBase58WithChecksum
import com.tangem.crypto.hdWallet.DerivationNode
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.crypto.hdWallet.HDWalletError
import com.tangem.crypto.hdWallet.bip32.serialization.ExtendedKeySerializable
import com.tangem.crypto.hdWallet.bip32.serialization.ExtendedKeySerializationError
import com.tangem.crypto.hdWallet.bip32.serialization.ExtendedKeySerializer
import com.tangem.crypto.hmacSha512
import com.tangem.operations.CommandResponse

@JsonClass(generateAdapter = true)
class ExtendedPrivateKey @Throws constructor(
    val privateKey: ByteArray,
    val chainCode: ByteArray,
    val depth: Int = 0,
    val parentFingerprint: ByteArray = "0x00000000".hexToBytes(),
    val childNumber: Long = 0L,
) : CommandResponse, ExtendedKeySerializable {

    init {
        if (depth == 0 && (parentFingerprint.first().toInt() != 0 || childNumber != 0L)) {
            throw ExtendedKeySerializationError.WrongKey
        }
    }

    fun makePublicKey(curve: EllipticCurve): ExtendedPublicKey {
        val publicKey = CryptoUtils.generatePublicKey(privateKey, curve, true)
        return ExtendedPublicKey(
            publicKey = publicKey,
            chainCode = chainCode,
            depth = depth,
            parentFingerprint = parentFingerprint,
            childNumber = childNumber,
        )
    }

    /**
     * This function performs CKDpriv((kpar, cpar), i) → (ki, ci) to compute a child extended private key from
     * the parent extended private key.
     * It is defined for both hardened and non-hardened child keys. `secp256k1` only.
     * In case the derived key is invalid, the derivation proceeds with the next index, as required by BIP32.
     */
    @Suppress("MagicNumber")
    @Throws(HDWalletError::class)
    fun derivePrivateKey(node: DerivationNode): ExtendedPrivateKey {
        if (!CryptoUtils.isPrivateKeyValid(privateKey)) {
            throw TangemSdkError.UnsupportedCurve()
        }

        val parentPublicKey = Secp256k1.generatePublicKey(privateKey, compressed = true)

        return BIP32.deriveWithRetry(node.index) { index ->
            // let I = HMAC-SHA512(Key = cpar, Data = 0x00 || ser256(kpar) || ser32(i)) for hardened keys
            // let I = HMAC-SHA512(Key = cpar, Data = serP(point(kpar)) || ser32(i)) for non-hardened keys
            val data = if (index >= BIP32.Constants.hardenedOffset) {
                byteArrayOf(0) + privateKey + index.toByteArray(4)
            } else {
                parentPublicKey + index.toByteArray(4)
            }
            val digest = chainCode.hmacSha512(data)

            makeChildKey(digest = digest, index = index, parentPublicKey = parentPublicKey)
        }
    }

    /**
     * This function performs CKDpriv((kpar, cpar), i) → (ki, ci) to compute a child extended private key from
     * the parent extended private key.
     * It is defined for both hardened and non-hardened child keys. `secp256k1` only.
     * In case the derived key is invalid, the derivation proceeds with the next index, as required by BIP32.
     */
    @Throws(HDWalletError::class)
    fun derivePrivateKey(derivationPath: DerivationPath): ExtendedPrivateKey {
        var key: ExtendedPrivateKey = this
        derivationPath.nodes.forEach {
            key = key.derivePrivateKey(it)
        }
        return key
    }

    @Suppress("MagicNumber")
    @Throws(HDWalletError::class)
    fun makeChildKey(digest: ByteArray, index: Long, parentPublicKey: ByteArray): ExtendedPrivateKey {
        val derivedPrivateKey = try {
            Secp256k1.tweakAdd(privateKey, digest.sliceArray(0 until 32))
        } catch (e: Exception) {
            // BIP32: parse256(IL) ≥ n or ki = 0
            throw HDWalletError.InvalidChildKey
        }

        return ExtendedPrivateKey(
            privateKey = derivedPrivateKey,
            chainCode = digest.sliceArray(32 until 64),
            depth = depth + 1,
            parentFingerprint = parentPublicKey.calculateSha256().calculateRipemd160().take(4).toByteArray(),
            childNumber = index,
        )
    }

    fun serializeToWIFCompressed(networkType: NetworkType): String {
        return WIF.encodeToWIFCompressed(privateKey, networkType)
    }

    @Throws(Exception::class)
    override fun serialize(networkType: NetworkType): String {
        val version = ExtendedKeySerializer.Version.Private
        val prefix = version.getPrefix(networkType)

        val data = prefix.toByteArray(size = 4) +
            byteArrayOf(depth.toByte()) +
            parentFingerprint +
            childNumber.toByteArray(size = 4) +
            chainCode +
            byteArrayOf(0) + privateKey

        if (data.size != ExtendedKeySerializer.Constants.DATA_LENGTH) {
            throw ExtendedKeySerializationError.WrongLength
        }

        return data.encodeToBase58WithChecksum()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ExtendedPrivateKey

        if (!privateKey.contentEquals(other.privateKey)) return false
        if (!chainCode.contentEquals(other.chainCode)) return false
        if (depth != other.depth) return false
        if (!parentFingerprint.contentEquals(other.parentFingerprint)) return false
        if (childNumber != other.childNumber) return false

        return true
    }

    override fun hashCode(): Int {
        var result = privateKey.contentHashCode()
        result = 31 * result + chainCode.contentHashCode()
        result = 31 * result + depth
        result = 31 * result + parentFingerprint.contentHashCode()
        result = 31 * result + childNumber.hashCode()
        return result
    }

    companion object {
        @Suppress("MagicNumber")
        fun from(extendedKeyString: String, networkType: NetworkType): ExtendedKeySerializable {
            val data = try {
                extendedKeyString.decodeBase58WithChecksum()
            } catch (e: Exception) {
                Log.error { e.stackTraceToString() }
                throw ExtendedKeySerializationError.DecodingFailed
            }

            if (data.size != ExtendedKeySerializer.Constants.DATA_LENGTH) {
                throw ExtendedKeySerializationError.WrongLength
            }

            val decodedVersion = data.copyOfRange(0, 4).toLong()
            val version = ExtendedKeySerializer.Version.Private
            val prefix = version.getPrefix(networkType)

            if (decodedVersion != prefix) throw ExtendedKeySerializationError.WrongVersion

            val depth = data.copyOfRange(4, 5).toInt()
            val parentFingerprint = data.copyOfRange(5, 9)
            val childNumber = data.copyOfRange(9, 13).toLong()
            val chainCode = data.copyOfRange(13, 45)
            val privateKey = data.copyOfRange(46, 78)
            val prefixByte = data.copyOfRange(45, 46)

            if (!prefixByte.contentEquals(byteArrayOf(0))) {
                throw ExtendedKeySerializationError.DecodingFailed
            }

            if (!CryptoUtils.isPrivateKeyValid(privateKey)) {
                throw TangemSdkError.UnsupportedCurve()
            }

            return ExtendedPrivateKey(
                privateKey = privateKey,
                chainCode = chainCode,
                depth = depth,
                parentFingerprint = parentFingerprint,
                childNumber = childNumber,
            )
        }
    }
}
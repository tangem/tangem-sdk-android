package com.tangem.operations.sign

import com.squareup.moshi.JsonClass
import com.tangem.crypto.hdWallet.DerivationPath

@JsonClass(generateAdapter = true)
data class Hash(
    val index: Int,
    val data: ByteArray,
)

@JsonClass(generateAdapter = true)
data class SignedHash(
    val index: Int,
    val data: ByteArray,
    val signature: ByteArray,
)

@JsonClass(generateAdapter = true)
data class Chunk(
    val hashSize: Int,
    val hashes: List<Hash>,
)

@JsonClass(generateAdapter = true)
data class SignedChunk(
    val signedHashes: List<SignedHash>,
)

@JsonClass(generateAdapter = true)
data class SignData(
    val derivationPath: DerivationPath,
    val hash: ByteArray,
    val publicKey: ByteArray,
)

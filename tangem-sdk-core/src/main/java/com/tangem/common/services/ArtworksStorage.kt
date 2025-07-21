package com.tangem.common.services

import com.tangem.common.extensions.toHexString
import com.tangem.operations.attestation.ArtworkSize
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

internal class ArtworksStorage(
    private val directory: File,
) {

    private val data = mutableMapOf<String, ArtworkData>()

    init {
        fetch()
    }

    fun get(cardId: String, cardPublicKey: ByteArray, size: ArtworkSize): ByteArray? {
        val artworkData = data[getStorageKey(cardId, cardPublicKey, size)] ?: return null
        return if (System.currentTimeMillis() < artworkData.timestamp + TimeUnit.DAYS.toMillis(CACHE_LIFE_TIME)) {
            artworkData.artwork
        } else {
            null
        }
    }

    fun store(cardId: String, cardPublicKey: ByteArray, size: ArtworkSize, artwork: ByteArray) {
        val key = getStorageKey(cardId, cardPublicKey, size)
        val artworkData = ArtworkData(artwork, System.currentTimeMillis())
        data[key] = artworkData
        val file = File(directory, key)
        FileOutputStream(file).use {
            it.write(artworkData.serialize())
        }
    }

    private fun fetch() {
        val storedData = mutableMapOf<String, ArtworkData>()

        directory.listFiles()?.forEach { file ->
            if (file.isFile) {
                val bytes = file.readBytes()
                storedData[file.name] = ArtworkData.deserialize(bytes)
            }
        }

        this.data.putAll(storedData)
    }

    private fun getStorageKey(cardId: String, cardPublicKey: ByteArray, size: ArtworkSize): String {
        return "${cardId}_${cardPublicKey.toHexString()}_${size.ordinal}"
    }

    private companion object {
        const val CACHE_LIFE_TIME = 7L
    }

    class ArtworkData(
        val artwork: ByteArray,
        val timestamp: Long,
    ) {
        fun serialize(): ByteArray {
            return ByteBuffer.allocate(1 + Long.SIZE_BYTES + artwork.size).apply {
                put(SCHEME_VERSION.toByte())
                putLong(timestamp)
                put(artwork)
            }.array()
        }

        companion object {
            private const val SCHEME_VERSION = 1

            fun deserialize(data: ByteArray): ArtworkData {
                val buffer = ByteBuffer.wrap(data)
                val version = buffer.get().toInt()
                require(version == SCHEME_VERSION) { "Unsupported scheme version: $version" }
                val timestamp = buffer.long
                val artwork = ByteArray(buffer.remaining())
                buffer.get(artwork)
                return ArtworkData(artwork, timestamp)
            }
        }
    }
}
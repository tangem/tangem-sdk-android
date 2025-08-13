package com.tangem.operations.attestation

import com.tangem.Log
import com.tangem.common.card.EllipticCurve
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.services.ArtworksStorage
import com.tangem.common.services.Result
import com.tangem.crypto.CryptoUtils
import com.tangem.operations.attestation.api.BaseUrl
import com.tangem.operations.attestation.api.TangemApiService
import com.tangem.operations.attestation.api.models.CardArtworksResponse
import com.tangem.operations.attestation.verification.ManufacturerPublicKey
import com.tangem.operations.attestation.verification.ManufacturerPublicKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class CardArtworksProvider(
    private val isTangemAttestationProdEnv: Boolean,
    private val artworksDirectory: File,
) {

    private val service: TangemApiService by lazy { TangemApiService(isTangemAttestationProdEnv) }
    private val okHttpClient: OkHttpClient by lazy { OkHttpClient() }
    private val store = ArtworksStorage(artworksDirectory)

    suspend fun getArtwork(
        cardId: String,
        manufacturerName: String,
        firmwareVersion: FirmwareVersion,
        cardPublicKey: ByteArray,
        size: ArtworkSize,
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        store.get(cardId, cardPublicKey, size)?.let {
            Result.Success(it)
        } ?: when (val result = service.loadArtwork(cardId, cardPublicKey)) {
            is Result.Failure -> {
                Result.Failure(
                    TangemSdkError.NetworkError(customMessage = "Empty response: ${result.error.message}"),
                )
            }
            is Result.Success -> {
                val manufacturerPublicKey = ManufacturerPublicKeyProvider(firmwareVersion, manufacturerName).get()
                    ?: return@withContext Result.Failure(TangemSdkError.VerificationFailed())
                verifyArtwork(cardId, manufacturerPublicKey, cardPublicKey, size, result.data)
            }
        }
    }

    @Suppress("LongParameterList")
    private suspend fun verifyArtwork(
        cardId: String,
        manufacturerPublicKey: ManufacturerPublicKey,
        cardPublicKey: ByteArray,
        size: ArtworkSize,
        response: CardArtworksResponse,
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        if (response.imageLargeSignature.isEmpty() || response.imageLargeUrl.isEmpty()) {
            return@withContext Result.Failure(TangemSdkError.VerificationFailed())
        }

        val hasSmallImage = !response.imageSmallUrl.isNullOrEmpty() && !response.imageSmallSignature.isNullOrEmpty()

        val largeImage = getVerifiedImage(
            url = response.imageLargeUrl,
            signature = response.imageLargeSignature,
            size = ArtworkSize.LARGE,
            manufacturerPublicKey = manufacturerPublicKey,
        )
        if (largeImage is Result.Failure) {
            return@withContext Result.Failure(TangemSdkError.VerificationFailed())
        }
        val smallImage = if (hasSmallImage) {
            getVerifiedImage(
                url = requireNotNull(response.imageSmallUrl),
                signature = requireNotNull(response.imageSmallSignature),
                size = ArtworkSize.SMALL,
                manufacturerPublicKey = manufacturerPublicKey,
            )
        } else {
            null
        }
        storeImages(largeImage, smallImage, cardId, cardPublicKey)

        when (size) {
            ArtworkSize.LARGE -> largeImage
            ArtworkSize.SMALL -> if (smallImage != null && smallImage is Result.Success) {
                smallImage
            } else {
                Log.error { "CardArtworksProvider: No small image" }
                largeImage
            }
        }
    }

    private suspend fun getVerifiedImage(
        url: String,
        manufacturerPublicKey: ManufacturerPublicKey,
        signature: String,
        size: ArtworkSize,
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        when (val imageResult = downloadImage(url)) {
            is Result.Failure -> imageResult
            is Result.Success -> {
                val artworkId = getArtworkIdFromUrl(url)
                val prefix = "artwork|${size.name.lowercase()}|$artworkId|".encodeToByteArray()
                val message = prefix + imageResult.data

                val isValid = CryptoUtils.verify(
                    publicKey = manufacturerPublicKey.value.hexToBytes(),
                    message = message,
                    signature = signature.hexToBytes(),
                    curve = EllipticCurve.Secp256k1,
                )

                if (isValid) {
                    Result.Success(imageResult.data)
                } else {
                    Result.Failure(TangemSdkError.VerificationFailed())
                }
            }
        }
    }

    private fun getArtworkIdFromUrl(url: String): String {
        return url.substringAfterLast('/').substringBeforeLast('.')
    }

    private suspend fun downloadImage(urlString: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val response = okHttpClient
                .newCall(Request.Builder().url(urlString).build())
                .execute()
            if (!response.isSuccessful) {
                return@withContext Result.Failure(TangemSdkError.NetworkError(response.code.toString()))
            }
            response.body?.bytes()?.let {
                Result.Success(it)
            } ?: Result.Failure(TangemSdkError.NetworkError("Empty response body"))
        } catch (throwable: Throwable) {
            Result.Failure(TangemSdkError.ExceptionError(throwable))
        }
    }

    private fun storeImages(
        largeImage: Result<ByteArray>,
        smallImage: Result<ByteArray>?,
        cardId: String,
        cardPublicKey: ByteArray,
    ) {
        if (smallImage != null && smallImage is Result.Success) {
            store.store(cardId, cardPublicKey, ArtworkSize.SMALL, smallImage.data)
        } else if (largeImage is Result.Success) {
            store.store(cardId, cardPublicKey, ArtworkSize.SMALL, largeImage.data)
        }
        if (largeImage is Result.Success) {
            store.store(cardId, cardPublicKey, ArtworkSize.LARGE, largeImage.data)
        }
    }

    companion object {

        fun getUrlForArtwork(cardId: String, cardPublicKey: String, artworkId: String): String {
            return BaseUrl.VERIFY.url + "card/artwork" + "?artworkId=$artworkId&CID=$cardId&publicKey=$cardPublicKey"
        }
    }
}
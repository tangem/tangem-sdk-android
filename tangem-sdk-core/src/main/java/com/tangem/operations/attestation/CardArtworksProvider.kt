package com.tangem.operations.attestation

import com.tangem.Log
import com.tangem.common.card.EllipticCurve
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.services.Result
import com.tangem.crypto.CryptoUtils
import com.tangem.operations.attestation.api.BaseUrl
import com.tangem.operations.attestation.api.TangemApiService
import com.tangem.operations.attestation.api.models.CardArtworksResponse
import com.tangem.operations.attestation.verification.ManufacturerPublicKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

class CardArtworksProvider(
    private val isTangemAttestationProdEnv: Boolean,
) {

    private val service: TangemApiService by lazy { TangemApiService(isTangemAttestationProdEnv) }
    private val okHttpClient: OkHttpClient by lazy { OkHttpClient() }
    private var publicKeyProvider: ManufacturerPublicKeyProvider? = null

    private val cachedArtworks: ConcurrentHashMap<String, ByteArray> = ConcurrentHashMap()

    suspend fun getArtwork(
        cardId: String,
        manufacturerName: String,
        firmwareVersion: FirmwareVersion,
        cardPublicKey: ByteArray,
        size: ArtworkSize,
    ): Result<ByteArray> {
        return cachedArtworks[getStorageKey(cardId, cardPublicKey, size)]?.let {
            Result.Success(it)
        } ?: when (val result = service.loadArtwork(cardId, cardPublicKey)) {
            is Result.Failure -> {
                Result.Failure(TangemSdkError.NetworkError(customMessage = "Empty response: ${result.error.message}"))
            }
            is Result.Success -> {
                verifyArtwork(cardId, manufacturerName, firmwareVersion, cardPublicKey, size, result.data)
            }
        }
    }

    @Suppress("LongParameterList")
    private suspend fun verifyArtwork(
        cardId: String,
        manufacturerName: String,
        firmwareVersion: FirmwareVersion,
        cardPublicKey: ByteArray,
        size: ArtworkSize,
        response: CardArtworksResponse,
    ): Result<ByteArray> {
        if (publicKeyProvider == null) {
            publicKeyProvider = ManufacturerPublicKeyProvider(firmwareVersion, manufacturerName)
        }
        val hasSmallImage = !response.imageSmallUrl.isNullOrEmpty() && !response.imageSmallSignature.isNullOrEmpty()

        val (imageUrl, signature) = if (size == ArtworkSize.SMALL && hasSmallImage) {
            response.imageSmallUrl to response.imageSmallSignature
        } else {
            response.imageLargeUrl to response.imageLargeSignature
        }

        if (imageUrl == null || signature == null) {
            return Result.Failure(TangemSdkError.VerificationFailed())
        }

        val largeImage = getVerifiedImage(response.imageLargeUrl, response.imageLargeSignature, ArtworkSize.LARGE)
        val smallImage = if (hasSmallImage) {
            getVerifiedImage(
                url = requireNotNull(response.imageSmallUrl),
                signature = requireNotNull(response.imageSmallSignature),
                size = ArtworkSize.SMALL,
            )
        } else {
            null
        }
        storeImages(largeImage, smallImage, cardId, cardPublicKey)

        return when (size) {
            ArtworkSize.LARGE -> largeImage
            ArtworkSize.SMALL -> smallImage ?: run {
                Log.error { "CardArtworksProvider: No small image" }
                largeImage
            }
        }
    }

    private suspend fun getVerifiedImage(url: String, signature: String, size: ArtworkSize): Result<ByteArray> {
        return when (val imageResult = downloadImage(url)) {
            is Result.Failure -> imageResult
            is Result.Success -> {
                val artworkId = getArtworkIdFromUrl(url)
                val prefix = "artwork|${size.name.lowercase()}|$artworkId|".encodeToByteArray()
                val message = prefix + imageResult.data
                val manufacturerPublicKey = publicKeyProvider?.get()
                    ?: return Result.Failure(TangemSdkError.VerificationFailed())

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

    private suspend fun downloadImage(urlString: String): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
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
    }

    private fun storeImages(
        largeImage: Result<ByteArray>,
        smallImage: Result<ByteArray>?,
        cardId: String,
        cardPublicKey: ByteArray,
    ) {
        if (smallImage != null && smallImage is Result.Success) {
            cachedArtworks[getStorageKey(cardId, cardPublicKey, ArtworkSize.SMALL)] = smallImage.data
        }
        if (largeImage is Result.Success) {
            cachedArtworks[getStorageKey(cardId, cardPublicKey, ArtworkSize.LARGE)] = largeImage.data
        }
    }

    private fun getStorageKey(cardId: String, cardPublicKey: ByteArray, size: ArtworkSize): String {
        return "${cardId}_${cardPublicKey}_${size.ordinal}"
    }

    companion object {

        fun getUrlForArtwork(cardId: String, cardPublicKey: String, artworkId: String): String {
            return BaseUrl.VERIFY.url + "card/artwork" + "?artworkId=$artworkId&CID=$cardId&publicKey=$cardPublicKey"
        }
    }
}
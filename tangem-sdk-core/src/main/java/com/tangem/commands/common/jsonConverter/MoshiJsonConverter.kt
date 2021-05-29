package com.tangem.commands.common.jsonConverter

import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tangem.commands.common.card.FirmwareVersion
import com.tangem.commands.common.card.masks.ProductMask
import com.tangem.commands.common.card.masks.SettingsMask
import com.tangem.commands.common.card.masks.SigningMethodMask
import com.tangem.commands.common.card.masks.WalletSettingsMask
import com.tangem.commands.wallet.WalletIndex
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.extensions.toHexString
import java.lang.reflect.ParameterizedType
import java.text.DateFormat
import java.util.*
import kotlin.reflect.KClass

/**
[REDACTED_AUTHOR]
 */
class MoshiJsonConverter(adapters: List<Any> = listOf()) {

    val moshi: Moshi = Moshi.Builder().apply {
        adapters.forEach { this.add(it) }
        add(KotlinJsonAdapterFactory())
    }.build()

    inline fun <reified T> fromJson(json: String): T? {
        val adapter = moshi.adapter(T::class.java)
        return adapter.fromJson(json)
    }

    fun <T> fromJson(json: String, type: ParameterizedType): T? {
        return moshi.adapter<T>(type).fromJson(json)
    }

    fun toJson(any: Any?, indent: String = "   "): String {
        return when (any) {
            null -> "{}"
            else -> moshi.adapter(Any::class.java).indent(indent).toJson(any)
        }
    }

    fun toMap(any: Any?): Map<String, Any> {
        val result: Map<String, Any>? = when (any) {
            null -> null
            is String -> fromJson(any, typedMap())
            else -> fromJson(toJson(any), typedMap())
        }
        return result ?: mapOf()
    }

    fun typedList(clazz: Class<*>): ParameterizedType {
        return Types.newParameterizedType(List::class.java, clazz)
    }

    fun typedMap(key: KClass<*> = String::class, value: KClass<*> = Any::class): ParameterizedType {
        return Types.newParameterizedType(Map::class.java, key.javaObjectType, value.javaObjectType)
    }

    companion object {
        val INSTANCE: MoshiJsonConverter by lazy { tangemSdkJsonConverter() }

        fun tangemSdkJsonConverter(): MoshiJsonConverter {
            return MoshiJsonConverter(getTangemSdkAdapters())
        }

        fun getTangemSdkAdapters(): List<Any> {
            return listOf(
                TangemSdkAdapter.ByteTypeAdapter(),
                TangemSdkAdapter.SigningMethodTypeAdapter(),
                TangemSdkAdapter.SettingsMaskTypeAdapter(),
                TangemSdkAdapter.ProductMaskTypeAdapter(),
                TangemSdkAdapter.WalletSettingsMaskAdapter(),
                TangemSdkAdapter.DateTypeAdapter(),
                TangemSdkAdapter.FirmwareVersionAdapter(),
                TangemSdkAdapter.WalletIntIndexAdapter(),
                TangemSdkAdapter.WalletPubKeyIndexAdapter(),
                TangemSdkAdapter.WalletIndexAdapter(),
            )
        }
    }
}

class TangemSdkAdapter {
    class ByteTypeAdapter {
        @ToJson
        fun toJson(src: ByteArray): String = src.toHexString()

        @FromJson
        fun fromJson(json: String): ByteArray = json.hexToBytes()
    }

    class SettingsMaskTypeAdapter {
        @ToJson
        fun toJson(src: SettingsMask): String = src.toString()

        @FromJson
        fun fromJson(json: String): SettingsMask = SettingsMask.fromString(json)
    }

    class ProductMaskTypeAdapter {
        @ToJson
        fun toJson(src: ProductMask): String = src.toString()

        @FromJson
        fun fromJson(json: String): ProductMask = ProductMask.fromString(json)
    }

    class SigningMethodTypeAdapter {
        @ToJson
        fun toJson(src: SigningMethodMask): String = src.toString()

        @FromJson
        fun fromJson(json: String): SigningMethodMask = SigningMethodMask.fromString(json)
    }

    class WalletSettingsMaskAdapter {
        @ToJson
        fun toJson(src: WalletSettingsMask): String = src.toString()

        @FromJson
        fun fromJson(json: String): WalletSettingsMask = WalletSettingsMask.fromString(json)
    }

    class DateTypeAdapter {
        private val dateFormatter = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale("en_US"))

        @ToJson
        fun toJson(src: Date): String {
            return dateFormatter.format(src).toString()
        }

        @FromJson
        fun fromJson(json: String): Date = dateFormatter.parse(json)
    }

    class FirmwareVersionAdapter {
        @ToJson
        fun toJson(src: FirmwareVersion): String = src.version

        @FromJson
        fun fromJson(json: String): FirmwareVersion = FirmwareVersion(json)
    }

    class WalletIntIndexAdapter {
        @ToJson
        fun toJson(src: WalletIndex.Index): String = src.index.toString()

        @FromJson
        fun fromJson(json: String): WalletIndex.Index = WalletIndex.Index(json.toInt())
    }

    class WalletPubKeyIndexAdapter {
        @ToJson
        fun toJson(src: WalletIndex.PublicKey): String = src.data.toHexString()

        @FromJson
        fun fromJson(json: String): WalletIndex.PublicKey {
            return WalletIndex.PublicKey(json.hexToBytes())
        }
    }

    class WalletIndexAdapter {
        @ToJson
        fun toJson(src: WalletIndex): String {
            return when (src) {
                is WalletIndex.Index -> WalletIntIndexAdapter().toJson(src)
                is WalletIndex.PublicKey -> WalletPubKeyIndexAdapter().toJson(src)
            }
        }

        @FromJson
        fun fromJson(json: String): WalletIndex {
            val possibleIntIndex = json.toIntOrNull()
            return if (possibleIntIndex != null) {
                WalletIntIndexAdapter().fromJson(json)
            } else {
                WalletPubKeyIndexAdapter().fromJson(json)
            }
        }
    }
}
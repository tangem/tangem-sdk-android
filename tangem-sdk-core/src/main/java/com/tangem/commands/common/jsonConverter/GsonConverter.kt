package com.tangem.commands.common.jsonConverter

import com.google.gson.*
import com.tangem.commands.CommandResponse
import com.tangem.commands.common.card.masks.*
import com.tangem.common.extensions.print
import com.tangem.common.extensions.toHexString
import java.lang.reflect.Type
import java.text.DateFormat
import java.util.*

/**
[REDACTED_AUTHOR]
 */
@Deprecated(
    "It's bean deleted later",
    ReplaceWith("com.tangem.coommands.common.jsonConverter.MoshiConverter")
)
class ResponseConverter {

    val gson: Gson by lazy { init() }

    private val fieldConverter = ResponseFieldConverter()

    private fun init(): Gson {
        val builder = GsonBuilder().apply {
            registerTypeAdapter(ByteArray::class.java, ByteTypeAdapter(fieldConverter))
            registerTypeAdapter(SigningMethodMask::class.java, SigningMethodTypeAdapter(fieldConverter))
            registerTypeAdapter(SettingsMask::class.java, SettingsMaskTypeAdapter(fieldConverter))
            registerTypeAdapter(ProductMask::class.java, ProductMaskTypeAdapter(fieldConverter))
            registerTypeAdapter(Date::class.java, DateTypeAdapter())
        }
        builder.setPrettyPrinting()
        return builder.create()
    }

    fun toJson(response: CommandResponse?): String = gson.toJson(response)
}

class ByteTypeAdapter(
    private val fieldConverter: ResponseFieldConverter
) : JsonSerializer<ByteArray> {
    override fun serialize(src: ByteArray, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonPrimitive(fieldConverter.byteArrayToHex(src))
    }
}

class SettingsMaskTypeAdapter(
    private val fieldConverter: ResponseFieldConverter
) : JsonSerializer<SettingsMask> {
    override fun serialize(src: SettingsMask, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonArray().apply {
            fieldConverter.settingsMaskList(src).forEach { add(it) }
        }
    }
}

class ProductMaskTypeAdapter(
    private val fieldConverter: ResponseFieldConverter
) : JsonSerializer<ProductMask> {
    override fun serialize(src: ProductMask, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonArray().apply {
            fieldConverter.productMaskList(src).forEach { add(it) }
        }
    }
}

class SigningMethodTypeAdapter(
    private val fieldConverter: ResponseFieldConverter
) : JsonSerializer<SigningMethodMask> {
    override fun serialize(src: SigningMethodMask, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonArray().apply {
            fieldConverter.signingMethodList(src).forEach { add(it) }
        }
    }
}

class DateTypeAdapter : JsonSerializer<Date> {
    override fun serialize(src: Date, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val formatter = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale("en_US"))
        return JsonPrimitive(formatter.format(src).toString())
    }
}
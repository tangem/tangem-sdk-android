package com.tangem.commands.common.jsonConverter

import com.tangem.commands.common.card.masks.*
import com.tangem.common.extensions.print
import com.tangem.common.extensions.toHexString

/**
[REDACTED_AUTHOR]
 */
@Deprecated("To convert the Masks use the appropriate class methods")
class ResponseFieldConverter {

    fun productMask(productMask: ProductMask?): String {
        return productMaskList(productMask).print(wrap = false)
    }

    fun productMaskList(productMask: ProductMask?): List<String> {
        val mask = productMask ?: return emptyList()

        return Product.values().filter { mask.contains(it) }.map { it.name }
    }

    fun signingMethod(signingMask: SigningMethodMask?): String {
        return signingMethodList(signingMask).print(wrap = false)
    }

    fun signingMethodList(signingMask: SigningMethodMask?): List<String> {
        val mask = signingMask ?: return emptyList()

        return SigningMethod.values().filter { mask.contains(it) }.map { it.name }
    }

    fun settingsMask(settingsMask: SettingsMask?): String {
        return settingsMaskList(settingsMask).print(wrap = false)
    }

    fun settingsMaskList(settingsMask: SettingsMask?): List<String> {
        val masks = settingsMask ?: return emptyList()

        return Settings.values().filter { masks.contains(it) }.map { it.name }
    }

    fun byteArrayToHex(byteArray: ByteArray?): String? {
        return byteArray?.toHexString()
    }

    fun byteArrayToString(byteArray: ByteArray?): String? {
        return if (byteArray == null) null else String(byteArray)
    }
}
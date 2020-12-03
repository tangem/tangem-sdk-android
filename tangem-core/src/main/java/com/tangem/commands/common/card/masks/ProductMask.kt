package com.tangem.commands.common.card.masks

/**
 * Mask of products enabled on card
 * @property rawValue Products mask values,
 * while flags definitions and values are in [ProductMask.Companion] as constants.
 */
data class ProductMask(val rawValue: Int) {

    fun contains(product: Product): Boolean = (rawValue and product.code) != 0

}

enum class Product(val code: Int) {
    Note(0x01),
    Tag(0x02),
    IdCard(0x04),
    IdIssuer(0x08),
    TwinCard(0x20),
}

class ProductMaskBuilder() {

    private var productMaskValue = 0

    fun add(product: Product) {
        productMaskValue = productMaskValue or product.code
    }

    fun build() = ProductMask(productMaskValue)

}
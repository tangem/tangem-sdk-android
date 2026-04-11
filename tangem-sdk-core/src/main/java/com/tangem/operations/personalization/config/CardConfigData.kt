package com.tangem.operations.personalization.config

import com.squareup.moshi.Json
import com.tangem.operations.personalization.entities.ProductMask
import java.util.Date

@Suppress("LongParameterList")
class CardConfigData(
    val date: Date?,
    val batch: String,
    val blockchain: String?,
    @Json(name = "product_note") val productNote: Boolean?,
    @Json(name = "product_tag") val productTag: Boolean?,
    @Json(name = "product_id_card") val productIdCard: Boolean?,
    @Json(name = "product_id_issuer") val productIdIssuer: Boolean?,
    @Json(name = "product_authentication") val productAuthentication: Boolean?,
    @Json(name = "product_twin_card") val productTwin: Boolean?,
    @Json(name = "token_symbol") val tokenSymbol: String?,
    @Json(name = "token_contract_address") val tokenContractAddress: String?,
    @Json(name = "token_decimal") val tokenDecimal: Int?,
) {

    internal fun createPersonalizationCardData(): CardData {
        return CardData(
            batch,
            date ?: Date(),
            blockchain ?: "",
            createProductMask(),
            tokenSymbol,
            tokenContractAddress,
            tokenDecimal,
        )
    }

    private fun createProductMask(): ProductMask {
        val builder = MaskBuilder()
        if (productNote == true) {
            builder.add(ProductMask.Code.Note)
        }
        if (productTag == true) {
            builder.add(ProductMask.Code.Tag)
        }
        if (productIdCard == true) {
            builder.add(ProductMask.Code.IdCard)
        }
        if (productIdIssuer == true) {
            builder.add(ProductMask.Code.IdIssuer)
        }
        if (productAuthentication == true) {
            builder.add(ProductMask.Code.Authentication)
        }
        if (productTwin == true) {
            builder.add(ProductMask.Code.TwinCard)
        }

        return builder.build()
    }
}
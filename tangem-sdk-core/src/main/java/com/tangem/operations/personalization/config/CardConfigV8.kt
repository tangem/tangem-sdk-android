package com.tangem.operations.personalization.config

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.tangem.common.card.EllipticCurve
import com.tangem.common.card.SigningMethod
import com.tangem.common.extensions.calculateSha256
import com.tangem.operations.personalization.entities.NdefRecord

@JsonClass(generateAdapter = true)
data class CardConfigV8(
    internal val releaseVersion: Boolean,
    internal val issuerName: String,
    internal val series: String?,
    internal val startNumber: Long,
    internal val count: Int,
    internal val numberFormat: String,
    @Json(name = "PIN")
    internal val pin: String,

    internal val securityDelay: Int,

    internal val curveID: EllipticCurve?,
    @Json(name = "SigningMethod")
    internal val signingMethod: SigningMethod?,
    @Json(name = "allowSwapPIN")
    internal val allowSetPIN: Boolean,

    internal val useActivation: Boolean,

    internal val useNDEF: Boolean,
    internal val useBlock: Boolean,

    @Json(name = "forbidPurgeWallet")
    internal val prohibitPurgeWallet: Boolean,
    @Json(name = "forbidDefaultPIN")
    internal val prohibitDefaultPIN: Boolean,

    internal val disableFiles: Boolean?,
    internal val allowHDWallets: Boolean?,
    internal val allowBackup: Boolean?,
    internal val allowKeysImport: Boolean?,
    internal val requireBackup: Boolean?,
    internal val createWallet: Int,
    internal val cardData: CardConfigData,
    @Json(name = "NDEF")
    val ndefRecords: List<NdefRecord>,
    /**
     * Number of wallets supported by card, by default - 1
     */
    internal val walletsCount: Int?,
) {

    fun pinSha256(): ByteArray = pin.calculateSha256()

}
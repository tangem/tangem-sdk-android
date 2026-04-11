package com.tangem.operations.personalization.config

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.tangem.common.card.EllipticCurve
import com.tangem.common.card.SigningMethod
import com.tangem.common.extensions.calculateSha256
import com.tangem.operations.personalization.entities.NdefRecord

/**
 * It is a configuration file with all the card settings that are written on the card
 * during [PersonalizeCommand].
 */
@JsonClass(generateAdapter = true)
data class CardConfig(
    internal val releaseVersion: Boolean,
    internal val issuerName: String,
    internal val series: String?,
    internal val startNumber: Long,
    internal val count: Int,
    internal val numberFormat: String,
    @Json(name = "PIN")
    internal val pin: String,
    @Json(name = "PIN2")
    internal val pin2: String,
    @Json(name = "PIN3")
    internal val pin3: String?,
    internal val hexCrExKey: ByteArray?,
    @Json(name = "CVC")
    internal val cvc: String,
    @Json(name = "pauseBeforePIN2")
    internal val pauseBeforePin2: Int,
    internal val smartSecurityDelay: Boolean,
    internal val curveID: EllipticCurve?,
    @Json(name = "SigningMethod")
    internal val signingMethod: SigningMethod,
    @Json(name = "MaxSignatures")
    internal val maxSignatures: Int?,
    @Json(name = "allowSwapPIN")
    internal val allowSetPIN1: Boolean,
    @Json(name = "allowSwapPIN2")
    internal val allowSetPIN2: Boolean,
    internal val useActivation: Boolean,
    @Json(name = "useCVC")
    internal val useCvc: Boolean,
    @Json(name = "useNDEF")
    internal val useNDEF: Boolean,
    internal val useDynamicNDEF: Boolean?,
    internal val useOneCommandAtTime: Boolean?,
    internal val useBlock: Boolean,
    internal val allowSelectBlockchain: Boolean?,
    @Json(name = "forbidPurgeWallet")
    internal val prohibitPurgeWallet: Boolean,
    @Json(name = "protocolAllowUnencrypted")
    internal val allowUnencrypted: Boolean,
    @Json(name = "protocolAllowStaticEncryption")
    internal val allowFastEncryption: Boolean,
    internal val protectIssuerDataAgainstReplay: Boolean?,
    @Json(name = "forbidDefaultPIN")
    internal val prohibitDefaultPIN1: Boolean,
    internal val disablePrecomputedNDEF: Boolean?,
    internal val skipSecurityDelayIfValidatedByIssuer: Boolean,
    @Json(name = "skipCheckPIN2andCVCIfValidatedByIssuer")
    internal val skipCheckPIN2CVCIfValidatedByIssuer: Boolean,
    internal val skipSecurityDelayIfValidatedByLinkedTerminal: Boolean,
    internal val restrictOverwriteIssuerDataEx: Boolean?,
    internal val disableIssuerData: Boolean?,
    internal val disableUserData: Boolean?,
    internal val disableFiles: Boolean?,
    internal val allowHDWallets: Boolean?,
    internal val allowBackup: Boolean?,
    internal val allowKeysImport: Boolean?,
    internal val createWallet: Int,
    internal val cardData: CardConfigData,
    @Json(name = "NDEF")
    val ndefRecords: List<NdefRecord>,
    /**
     * Number of wallets supported by card, by default - 1
     */
    internal val walletsCount: Int?,
    internal val isReusable: Boolean?,
) {

    fun pinSha256(): ByteArray = pin.calculateSha256()
    fun pin2Sha256(): ByteArray = pin2.calculateSha256()
    fun pin3Sha256(): ByteArray? = pin3?.calculateSha256()

}
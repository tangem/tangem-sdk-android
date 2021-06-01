package com.tangem.commands.personalization.entities

import com.tangem.commands.common.card.CardData
import com.tangem.commands.common.card.EllipticCurve
import com.tangem.commands.common.card.masks.SigningMethodMask
import com.tangem.common.extensions.calculateSha256

data class NdefRecord(
        val type: Type,
        val value: String
) {
    enum class Type {
        URI, AAR, TEXT
    }

    fun valueInBytes(): ByteArray = value.toByteArray()
}

/**
 * It is a configuration file with all the card settings that are written on the card
 * during [PersonalizeCommand].
 */
class CardConfig(
        val issuerName: String? = null,
        val acquirerName: String? = null,
        val series: String? = null,
        val startNumber: Long = 0,
        val count: Int = 0,
        val pin: String,
        val pin2: String,
        val pin3: String,
        val hexCrExKey: String?,
        val cvc: String,
        val pauseBeforePin2: Int,
        val smartSecurityDelay: Boolean,
        val curveID: EllipticCurve,
        val signingMethods: SigningMethodMask,
        val maxSignatures: Int,
        val isReusable: Boolean,
        val allowSetPIN1: Boolean,
        val allowSetPIN2: Boolean,
        val useActivation: Boolean,
        val useCvc: Boolean,
        val useNDEF: Boolean,
        val useDynamicNDEF: Boolean,
        val useOneCommandAtTime: Boolean,
        val useBlock: Boolean,
        val allowSelectBlockchain: Boolean,
        val prohibitPurgeWallet: Boolean,
        val allowUnencrypted: Boolean,
        val allowFastEncryption: Boolean,
        val protectIssuerDataAgainstReplay: Boolean,
        val prohibitDefaultPIN1: Boolean,
        val disablePrecomputedNDEF: Boolean,
        val skipSecurityDelayIfValidatedByIssuer: Boolean,
        val skipCheckPIN2CVCIfValidatedByIssuer: Boolean,
        val skipSecurityDelayIfValidatedByLinkedTerminal: Boolean,

        val restrictOverwriteIssuerExtraData: Boolean,

        val requireTerminalTxSignature: Boolean,
        val requireTerminalCertSignature: Boolean,
        val checkPIN3OnCard: Boolean,

        val createWallet: Boolean,
        val walletsCount: Int,

        val cardData: CardData,
        val ndefRecords: List<NdefRecord>
) {

    fun pinSha256(): ByteArray = pin.calculateSha256()
    fun pin2Sha256(): ByteArray = pin2.calculateSha256()
    fun pin3Sha256(): ByteArray = pin3.calculateSha256()

    companion object
}
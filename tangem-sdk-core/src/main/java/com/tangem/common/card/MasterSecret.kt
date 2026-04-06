package com.tangem.common.card

import com.squareup.moshi.JsonClass
import com.tangem.common.card.CardWallet.Status.Companion.initExtendedPublicKey
import com.tangem.crypto.hdWallet.bip32.ExtendedPublicKey

@JsonClass(generateAdapter = true)
data class MasterSecret(
    /**
     * Secret's public key.
     */
    val publicKey: ByteArray?,

    /**
     * Optional chain code for BIP32 derivation.
     */
    val chainCode: ByteArray?,

    /**
     *  Elliptic curve used for all wallet key operations.
     */
    val curve: EllipticCurve = EllipticCurve.Secp256k1,

    /**
     *  Has this key been imported to a card. E.g. from seed phrase
     */
    val isImported: Boolean,

    /**
     *  Shows whether this wallet has a backup
     */
    val hasBackup: Boolean,

    /**
     * Raw status of the wallet
     */
    val status: CardWallet.Status,

    ) {

    val extendedPublicKey: ExtendedPublicKey?
        get() = initExtendedPublicKey(publicKey, chainCode)
}
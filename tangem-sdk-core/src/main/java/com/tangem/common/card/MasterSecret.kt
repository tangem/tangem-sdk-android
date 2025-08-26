package com.tangem.common.card

import com.squareup.moshi.JsonClass
import com.tangem.common.BaseMask
import com.tangem.common.Mask
import com.tangem.common.card.CardWallet.Status.Companion.initExtendedPublicKey
import com.tangem.crypto.hdWallet.DerivationPath
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

) {

    val extendedPublicKey: ExtendedPublicKey?
        get() = initExtendedPublicKey(publicKey, chainCode)

    /**
     * Status of the wallet.
     */
    enum class Status(val code: Int) {
        /**

         */
        Empty(code = 1),

        /**

         */
        Loaded(code = 2),

        /**

         */
        EmptyBackedUp(code = 0x81),

        /**

         */
        BackedUp(code = 0x82),


        /**
         * Wallet was imported
         */
        Imported(code = 0x42),

        /**
         * Wallet was imported and backed up
         */
        BackedUpImported(code = 0xC2),
        ;

        val isBackedUp: Boolean
            get() = when (this) {
                BackedUp, BackedUpImported -> true
                else -> false
            }

        val isImported: Boolean
            get() = when (this) {
                Imported, BackedUpImported -> true
                else -> false
            }

        val isAvailable: Boolean
            get() = when (this) {
                Empty, EmptyBackedUp -> false
                else -> true
            }

        companion object {
            private val values = values()
            fun byCode(code: Int): Status? {
                return values.find { it.code.toByte() == code.toByte() }
            }

            fun initExtendedPublicKey(publicKey: ByteArray, chainCode: ByteArray?): ExtendedPublicKey? {
                val chainCode = chainCode ?: return null

                return ExtendedPublicKey(publicKey, chainCode)
            }
        }
    }
}
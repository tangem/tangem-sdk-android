package com.tangem.commands.wallet

import com.tangem.commands.common.card.EllipticCurve
import com.tangem.commands.common.card.masks.SettingsMask
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag

/**
[REDACTED_AUTHOR]
 *
 * @property index Index of wallet in card storage. Use this index to create `WalletIndex` for interaction with
 * wallet on card
 * @property status Current status of wallet. Statuses: empty = 1, loaded = 2, purged = 3
 * @property curve Explicit text name of the elliptic curve used for all wallet key operations. Supported curves:
 * ‘secp256k1’ and ‘ed25519’.
 * @property settingsMask
 * @property publicKey Public key of the blockchain wallet.
 * @property signedHashes Total number of signed single hashes returned by the card in `SignCommand` responses since
 * card personalization. Sums up array elements within all `SignCommand`.
 * @property remainingSignatures Remaining number of `SignCommand` operations before the wallet will stop signing
 * transactions. Note: This counter were deprecated for cards with COS 4.0 and higher
 */
data class CardWallet constructor(
    val index: Int,
    val status: WalletStatus,
    val curve: EllipticCurve? = null,
    val settingsMask: SettingsMask? = null,
    val publicKey: ByteArray? = null,
    val signedHashes: Int? = null,
    val remainingSignatures: Int? = null
) {

    constructor(response: CreateWalletResponse, curve: EllipticCurve, settings: SettingsMask?) : this(
        response.walletIndex,
        WalletStatus.from(response.status),
        curve,
        settings,
        response.walletPublicKey,
        0,
        null
    )

    val intIndex: WalletIndex = WalletIndex.Index(index)

    val pubKeyIndex: WalletIndex?
        get() = if (publicKey == null) null else WalletIndex.PublicKey(publicKey)

    companion object {
        fun deserialize(decoder: TlvDecoder): CardWallet = CardWallet(
            decoder.decode(TlvTag.WalletsIndex),
            decoder.decode(TlvTag.Status),
            decoder.decodeOptional(TlvTag.CurveId),
            decoder.decodeOptional(TlvTag.SettingsMask),
            decoder.decodeOptional(TlvTag.WalletPublicKey),
            decoder.decodeOptional(TlvTag.WalletSignedHashes),
        )
    }
}
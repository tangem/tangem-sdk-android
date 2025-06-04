package com.tangem.common.core

import com.tangem.common.CardFilter
import com.tangem.common.UserCodeType
import com.tangem.common.card.EllipticCurve
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.operations.attestation.AttestationTask

class Config(
    /**
     * Enables or disables Linked Terminal feature. Default is **true**
     * Notes:
     * App can optionally generate ECDSA key pair Terminal_PrivateKey / Terminal_PublicKey. And then submit
     * Terminal_PublicKey to the card in any SIGN command. Once SIGN is successfully executed by COS (Card Operation
     * System), including PIN2 verification and/or completion of security delay, the submitted Terminal_PublicKey key
     * is stored by COS. After that, the App instance is deemed trusted by COS and COS will allow skipping security
     * delay for subsequent SIGN operations thus improving convenience without sacrificing security.
     *
     * In order to skip security delay, App should use Terminal_PrivateKey to compute the signature of the data being
     * submitted to SIGN command for signing and transmit this signature in Terminal_Transaction_Signature parameter
     * in the same SIGN command. COS will verify the correctness of Terminal_Transaction_Signature using previously
     * stored Terminal_PublicKey and, if correct, will skip security delay for the current SIGN operation.
     * If null, TangemSdk will turn on this feature automatically according to iPhone model
     * COS version 2.30 and later.
     */
    var linkedTerminal: Boolean? = null,

    /**
     * If not null, it will be used to validate Issuer data and issuer extra data.
     * If null, issuerPublicKey from current card will be used
     */
    var issuerPublicKey: ByteArray? = null,

    var handleErrors: Boolean = true,

    var howToIsEnabled: Boolean = true,

    /**
     * Full CID will be displayed, if null
     */
    var cardIdDisplayFormat: CardIdDisplayFormat = CardIdDisplayFormat.Full,

    /**
     * ScanTask or scanCard method in TangemSdk class will use this mode to attest the card
     */
    var attestationMode: AttestationTask.Mode = AttestationTask.Mode.Normal,

    var filter: CardFilter = CardFilter.default(),

    /**
     * Convert all secp256k1 signatures, produced by the card, to a lowers-S form. True by default
     */
    var canonizeSecp256k1Signatures: Boolean = true,

    /** A card with HD wallets feature enabled will derive keys automatically on "scan" and "createWallet".
     * Repeated items will be ignored.
     * All derived keys will be stored in [com.tangem.common.card.CardWallet.derivedKeys].
     * Only `secp256k1` and `ed25519` supported.
     */
    var defaultDerivationPaths: MutableMap<EllipticCurve, List<DerivationPath>> = mutableMapOf(),

    /**
     * User code [accessCode naming in ios] request policy.
     */
    var userCodeRequestPolicy: UserCodeRequestPolicy = UserCodeRequestPolicy.Default,

    /**
     * Options for displaying different tags on the scanning screen
     */
    var scanTagImage: ScanTagImage = ScanTagImage.GenericCard,

    var productType: ProductType = ProductType.ANY,

    var isNewOnlineAttestationEnabled: Boolean = false,

    var isTangemAttestationProdEnv: Boolean = true,
) {

    fun setupForProduct(type: ProductType) {
        when (type) {
            ProductType.CARD -> {
                productType = ProductType.CARD
                cardIdDisplayFormat = CardIdDisplayFormat.Full
            }

            ProductType.RING -> {
                productType = ProductType.RING
                cardIdDisplayFormat = CardIdDisplayFormat.None
            }

            ProductType.ANY -> {
                productType = ProductType.ANY
                cardIdDisplayFormat = CardIdDisplayFormat.Full
            }
        }
    }
}

sealed class ScanTagImage {

    object GenericCard : ScanTagImage()

    data class Image(
        val bitmapArray: ByteArray,
        val verticalOffset: Int = 0,
    ) : ScanTagImage()
}

sealed class CardIdDisplayFormat {

    object None : CardIdDisplayFormat()

    // /Full cardId splitted by 4 numbers
    object Full : CardIdDisplayFormat()

    // /n numbers from the end
    data class Last(val numbers: Int) : CardIdDisplayFormat()

    // /n numbers from the end with mask, e.g.  * * * 1234
    data class LastMasked(val numbers: Int, val mask: String = " * * * ") : CardIdDisplayFormat()

    // /n numbers from the end except last
    data class LastLuhn(val numbers: Int) : CardIdDisplayFormat()
}

sealed class UserCodeRequestPolicy {
    /**
     * Defines which type of user code was requested before card scan. Has no effect on [UserCodeRequestPolicy.Default]
     * */
    open val codeType: UserCodeType? = null

    /**
     * User code will be requested before card scan. Biometrics will be used if enabled and there are any saved codes.
     * Requires Android SDK >= 23
     * */
    class AlwaysWithBiometrics(override val codeType: UserCodeType) : UserCodeRequestPolicy()

    /**
     * User code will be requested before card scan.
     * */
    class Always(override val codeType: UserCodeType) : UserCodeRequestPolicy()

    /**
     * User code will be requested only if set on the card. Need scan the card twice.
     * */
    object Default : UserCodeRequestPolicy()
}

enum class ProductType {
    ANY,
    CARD,
    RING,
    ;
}

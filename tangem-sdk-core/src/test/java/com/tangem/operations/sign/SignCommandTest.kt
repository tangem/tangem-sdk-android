package com.tangem.operations.sign

import com.google.common.truth.Truth
import com.tangem.common.card.Card
import com.tangem.common.card.CardWallet
import com.tangem.common.card.EllipticCurve
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.card.SigningMethod
import com.tangem.common.core.TangemError
import com.tangem.common.core.TangemSdkError
import com.tangem.crypto.hdWallet.DerivationPath
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

internal class SignCommandTest {

    @Test
    fun walletNotFoundReturnsWalletNotFound() {
        val card = card()
        every { card.wallet(walletPublicKey) } returns null

        val error = performPreCheck(card = card)

        Truth.assertThat(error).isInstanceOf(TangemSdkError.WalletNotFound::class.java)
    }

    /**
     * Regression for [REDACTED_TASK_KEY]: signing a secp256r1 wallet with a derivation path must no longer
     * be rejected with UnsupportedCurve (40502), since secp256r1 supports derivation.
     */
    @Test
    fun secp256r1WithDerivationPathPassesPreCheck() {
        val card = card(firmwareVersion = FirmwareVersion.v8)
        every { card.wallet(walletPublicKey) } returns wallet(curve = EllipticCurve.Secp256r1)

        val error = performPreCheck(card = card, derivationPath = DerivationPath("m/0"))

        Truth.assertThat(error).isNull()
    }

    @Test
    fun secp256k1WithDerivationPathPassesPreCheck() {
        val card = card(firmwareVersion = FirmwareVersion.v8)
        every { card.wallet(walletPublicKey) } returns wallet(curve = EllipticCurve.Secp256k1)

        val error = performPreCheck(card = card, derivationPath = DerivationPath("m/0"))

        Truth.assertThat(error).isNull()
    }

    @Test
    fun nonDerivableCurveWithDerivationPathReturnsUnsupportedCurve() {
        val card = card(firmwareVersion = FirmwareVersion.v8)
        every { card.wallet(walletPublicKey) } returns wallet(curve = EllipticCurve.Bls12381G2)

        val error = performPreCheck(card = card, derivationPath = DerivationPath("m/0"))

        Truth.assertThat(error).isInstanceOf(TangemSdkError.UnsupportedCurve::class.java)
    }

    @Test
    fun derivationPathWithOldFirmwareReturnsNotSupportedFirmwareVersion() {
        val card = card(firmwareVersion = FirmwareVersion(major = 3, minor = 0))
        every { card.wallet(walletPublicKey) } returns wallet(curve = EllipticCurve.Secp256k1)

        val error = performPreCheck(card = card, derivationPath = DerivationPath("m/0"))

        Truth.assertThat(error).isInstanceOf(TangemSdkError.NotSupportedFirmwareVersion::class.java)
    }

    @Test
    fun derivationPathWithHdWalletDisabledReturnsHDWalletDisabled() {
        val card = card(firmwareVersion = FirmwareVersion.v8, isHDWalletAllowed = false)
        every { card.wallet(walletPublicKey) } returns wallet(curve = EllipticCurve.Secp256r1)

        val error = performPreCheck(card = card, derivationPath = DerivationPath("m/0"))

        Truth.assertThat(error).isInstanceOf(TangemSdkError.HDWalletDisabled::class.java)
    }

    @Test
    fun secp256r1WithoutDerivationPathPassesPreCheck() {
        val card = card(firmwareVersion = FirmwareVersion.v8)
        every { card.wallet(walletPublicKey) } returns wallet(curve = EllipticCurve.Secp256r1)

        val error = performPreCheck(card = card, derivationPath = null)

        Truth.assertThat(error).isNull()
    }

    @Test
    fun zeroRemainingSignaturesReturnsNoRemainingSignatures() {
        val card = card(firmwareVersion = FirmwareVersion.v8)
        every { card.wallet(walletPublicKey) } returns
            wallet(curve = EllipticCurve.Secp256k1, remainingSignatures = 0)

        val error = performPreCheck(card = card)

        Truth.assertThat(error).isInstanceOf(TangemSdkError.NoRemainingSignatures::class.java)
    }

    @Test
    fun signHashNotAvailableReturnsSignHashesNotAvailable() {
        val card = card(
            firmwareVersion = FirmwareVersion.v8,
            defaultSigningMethods = SigningMethod.build(SigningMethod.Code.SignRaw),
        )
        every { card.wallet(walletPublicKey) } returns wallet(curve = EllipticCurve.Secp256k1)

        val error = performPreCheck(card = card)

        Truth.assertThat(error).isInstanceOf(TangemSdkError.SignHashesNotAvailable::class.java)
    }

    private fun performPreCheck(card: Card, derivationPath: DerivationPath? = null): TangemError? {
        val command = SignCommand(
            hashes = arrayOf(ByteArray(HASH_SIZE)),
            walletPublicKey = walletPublicKey,
            derivationPath = derivationPath,
        )
        val method = SignCommand::class.java.getDeclaredMethod("performPreCheck", Card::class.java)
        method.isAccessible = true
        return method.invoke(command, card) as? TangemError?
    }

    private fun card(
        firmwareVersion: FirmwareVersion = FirmwareVersion.v8,
        isHDWalletAllowed: Boolean = true,
        defaultSigningMethods: SigningMethod? = null,
    ): Card {
        val settings: Card.Settings = mockk()
        every { settings.isHDWalletAllowed } returns isHDWalletAllowed
        every { settings.defaultSigningMethods } returns defaultSigningMethods

        val card: Card = mockk()
        every { card.firmwareVersion } returns firmwareVersion
        every { card.settings } returns settings
        return card
    }

    private fun wallet(curve: EllipticCurve, remainingSignatures: Int? = 100): CardWallet {
        val wallet: CardWallet = mockk()
        every { wallet.curve } returns curve
        every { wallet.remainingSignatures } returns remainingSignatures
        return wallet
    }

    private companion object {
        const val HASH_SIZE = 32
        val walletPublicKey = byteArrayOf(1, 2, 3, 4)
    }
}
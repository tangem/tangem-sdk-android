package com.tangem.operations.attestation

import com.google.common.truth.Truth.assertThat
import com.tangem.common.UserCode
import com.tangem.common.UserCodeType
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.Card
import com.tangem.common.card.CardWallet
import com.tangem.common.card.EllipticCurve
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.core.TangemError
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.toByteArray
import com.tangem.common.tlv.Tlv
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvTag
import com.tangem.crypto.CryptoUtils
import com.tangem.crypto.CryptoUtils.generatePublicKey
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.crypto.hdWallet.bip32.ExtendedPublicKey
import com.tangem.crypto.sign
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertThrows
import java.lang.reflect.InvocationTargetException

/**
 * Unit tests for the signature-verification logic of [AttestWalletKeyTask].
 *
 * The verification methods are private, so they are exercised through reflection. Real secp256k1
 * key pairs and signatures are produced in-test (matching the style of `CryptoUtilsTest`) so that
 * the cryptographic checks run end-to-end rather than against mocks.
 */
internal class AttestWalletKeyTaskTest {

    init {
        CryptoUtils.initCrypto()
    }

    private val curve = EllipticCurve.Secp256k1

    private val challenge = ByteArray(CHALLENGE_SIZE) { 1 }
    private val salt = ByteArray(SALT_SIZE) { 7 }
    private val publicKeySalt = ByteArray(SALT_SIZE) { 9 }

    private val walletPrivateKey = ByteArray(KEY_SIZE) { 1 }
    private val walletPublicKey = generatePublicKey(walletPrivateKey, curve)

    private val cardPrivateKey = ByteArray(KEY_SIZE) { 2 }
    private val cardPublicKey = generatePublicKey(cardPrivateKey, curve)

    private val derivedPrivateKey = ByteArray(KEY_SIZE) { 3 }
    private val derivedPublicKey = generatePublicKey(derivedPrivateKey, curve)

    // region verifyWalletSignature

    @Test
    fun verifyWalletSignature_validSignature_returnsTrue() {
        val response = response(walletSignature = (challenge + salt).sign(walletPrivateKey, curve))

        val isValid = task().invokeVerifyWalletSignature(response, wallet())

        assertThat(isValid).isTrue()
    }

    @Test
    fun verifyWalletSignature_walletPublicKeyMismatch_returnsFalse() {
        // Regression for the reference-equality bug: a wallet whose key differs from the requested one
        // must be rejected. ByteArray `!=` would have compared references and let this slip through.
        val foreignPublicKey = generatePublicKey(ByteArray(KEY_SIZE) { 9 }, curve)
        val response = response(walletSignature = (challenge + salt).sign(walletPrivateKey, curve))

        val isValid = task().invokeVerifyWalletSignature(response, wallet(publicKey = foreignPublicKey))

        assertThat(isValid).isFalse()
    }

    @Test
    fun verifyWalletSignature_tamperedSignature_returnsFalse() {
        val tampered = (challenge + salt).sign(walletPrivateKey, curve).also { it[0] = (it[0] + 1).toByte() }
        val response = response(walletSignature = tampered)

        val isValid = task().invokeVerifyWalletSignature(response, wallet())

        assertThat(isValid).isFalse()
    }

    @Test
    fun verifyWalletSignature_validDerivedKeySignature_returnsTrue() {
        val derivationPath = DerivationPath(rawPath = "m/0/1")
        val response = response(walletSignature = (challenge + salt).sign(derivedPrivateKey, curve))
        val wallet = wallet(
            derivedKeys = mapOf(derivationPath to ExtendedPublicKey(derivedPublicKey, ByteArray(KEY_SIZE) { 4 })),
        )

        val isValid = task(derivationPath = derivationPath).invokeVerifyWalletSignature(response, wallet)

        assertThat(isValid).isTrue()
    }

    @Test
    fun verifyWalletSignature_missingDerivedKey_throwsWalletNotFound() {
        val derivationPath = DerivationPath(rawPath = "m/0/1")
        val response = response(walletSignature = (challenge + salt).sign(derivedPrivateKey, curve))

        assertThrows(TangemSdkError.WalletNotFound::class.java) {
            task(derivationPath = derivationPath).invokeVerifyWalletSignature(response, wallet())
        }
    }

    // endregion

    // region verifyCardSignature

    @Test
    fun verifyCardSignature_noneMode_absentSignature_returnsTrue() {
        // The card signature was never requested, so its absence is expected and accepted.
        val response = response(walletSignature = byteArrayOf(), cardSignature = null)

        val isValid = task(confirmationMode = AttestWalletKeyTask.ConfirmationMode.None)
            .invokeVerifyCardSignature(response, cardMock())

        assertThat(isValid).isTrue()
    }

    @Test
    fun verifyCardSignature_modernFirmware_expectedSignatureAbsent_returnsFalse() {
        // [REDACTED_TASK_KEY]: the signature was requested (Dynamic mode, COS 2.1+) but the card returned none.
        // Fail closed instead of accepting an unverified response.
        val response = response(walletSignature = byteArrayOf(), cardSignature = null)

        val isValid = task(confirmationMode = AttestWalletKeyTask.ConfirmationMode.Dynamic)
            .invokeVerifyCardSignature(response, cardMock(firmwareVersion = FirmwareVersion(2, 1)))

        assertThat(isValid).isFalse()
    }

    @Test
    fun verifyCardSignature_legacyFirmware_absentSignature_returnsTrue() {
        // On COS < 2.1 the signature is never requested (see the gate in `serialize`), so its absence
        // is expected even in Dynamic mode.
        val response = response(walletSignature = byteArrayOf(), cardSignature = null)

        val isValid = task(confirmationMode = AttestWalletKeyTask.ConfirmationMode.Dynamic)
            .invokeVerifyCardSignature(response, cardMock(firmwareVersion = FirmwareVersion(1, 0)))

        assertThat(isValid).isTrue()
    }

    @Test
    fun verifyCardSignature_dynamicMode_presentSignatureWithoutPublicKeySalt_returnsFalse() {
        // Downgrade guard: Dynamic was requested, but the response carries no `publicKeySalt`. Verifying
        // it against a static message would let a previously recorded static signature pass.
        val staticSignature = walletPublicKey.sign(cardPrivateKey, curve)
        val response = response(
            walletSignature = byteArrayOf(),
            cardSignature = staticSignature,
            publicKeySalt = null,
        )

        val isValid = task(confirmationMode = AttestWalletKeyTask.ConfirmationMode.Dynamic)
            .invokeVerifyCardSignature(response, cardMock())

        assertThat(isValid).isFalse()
    }

    @Test
    fun verifyCardSignature_validDynamicSignature_returnsTrue() {
        val message = walletPublicKey + challenge + publicKeySalt
        val response = response(
            walletSignature = byteArrayOf(),
            cardSignature = message.sign(cardPrivateKey, curve),
            publicKeySalt = publicKeySalt,
        )

        val isValid = task(confirmationMode = AttestWalletKeyTask.ConfirmationMode.Dynamic)
            .invokeVerifyCardSignature(response, cardMock())

        assertThat(isValid).isTrue()
    }

    @Test
    fun verifyCardSignature_validStaticSignature_returnsTrue() {
        // Static mode: the signed message is just the wallet public key, with no challenge/salt binding.
        val response = response(
            walletSignature = byteArrayOf(),
            cardSignature = walletPublicKey.sign(cardPrivateKey, curve),
            publicKeySalt = null,
        )

        val isValid = task(confirmationMode = AttestWalletKeyTask.ConfirmationMode.Static)
            .invokeVerifyCardSignature(response, cardMock())

        assertThat(isValid).isTrue()
    }

    @Test
    fun verifyCardSignature_tamperedSignature_returnsFalse() {
        val message = walletPublicKey + challenge + publicKeySalt
        val tampered = message.sign(cardPrivateKey, curve).also { it[0] = (it[0] + 1).toByte() }
        val response = response(
            walletSignature = byteArrayOf(),
            cardSignature = tampered,
            publicKeySalt = publicKeySalt,
        )

        val isValid = task(confirmationMode = AttestWalletKeyTask.ConfirmationMode.Dynamic)
            .invokeVerifyCardSignature(response, cardMock())

        assertThat(isValid).isFalse()
    }

    @Test
    fun verifyCardSignature_validDynamicSignatureWithWalletStatus_returnsTrue() {
        val status = CardWallet.Status.Loaded
        val message = walletPublicKey + challenge + publicKeySalt + status.code.toByteArray()
        val response = response(
            walletSignature = byteArrayOf(),
            cardSignature = message.sign(cardPrivateKey, curve),
            publicKeySalt = publicKeySalt,
            walletStatus = status,
        )

        val isValid = task(confirmationMode = AttestWalletKeyTask.ConfirmationMode.Dynamic)
            .invokeVerifyCardSignature(response, cardMock())

        assertThat(isValid).isTrue()
    }

    @Test
    fun verifyCardSignature_tamperedWalletStatus_returnsFalse() {
        // The signature covers `Loaded`, but the response claims `Empty` — the recomputed message differs,
        // so a swapped status must fail verification.
        val signedMessage = walletPublicKey + challenge + publicKeySalt + CardWallet.Status.Loaded.code.toByteArray()
        val response = response(
            walletSignature = byteArrayOf(),
            cardSignature = signedMessage.sign(cardPrivateKey, curve),
            publicKeySalt = publicKeySalt,
            walletStatus = CardWallet.Status.Empty,
        )

        val isValid = task(confirmationMode = AttestWalletKeyTask.ConfirmationMode.Dynamic)
            .invokeVerifyCardSignature(response, cardMock())

        assertThat(isValid).isFalse()
    }

    // endregion

    // region performPreCheck

    @Test
    fun performPreCheck_walletNotFound_returnsWalletNotFound() {
        val error = task().invokePerformPreCheck(preCheckCardMock(wallet = null))

        assertThat(error).isInstanceOf(TangemSdkError.WalletNotFound::class.java)
    }

    @Test
    fun performPreCheck_withoutDerivationPath_passes() {
        val error = task(derivationPath = null).invokePerformPreCheck(preCheckCardMock())

        assertThat(error).isNull()
    }

    @Test
    fun performPreCheck_derivationOnLegacyFirmware_returnsNotSupportedFirmwareVersion() {
        // HD derivation requires COS 4.39+ (FirmwareVersion.HDWalletAvailable).
        val error = task(derivationPath = DerivationPath(rawPath = "m/0/1"))
            .invokePerformPreCheck(preCheckCardMock(firmwareVersion = FirmwareVersion(4, 0)))

        assertThat(error).isInstanceOf(TangemSdkError.NotSupportedFirmwareVersion::class.java)
    }

    @Test
    fun performPreCheck_derivationOnUnsupportedCurve_returnsUnsupportedCurve() {
        // BLS curves do not support derivation (see EllipticCurve.supportsDerivation).
        val error = task(derivationPath = DerivationPath(rawPath = "m/0/1"))
            .invokePerformPreCheck(preCheckCardMock(wallet = wallet(curve = EllipticCurve.Bls12381G2)))

        assertThat(error).isInstanceOf(TangemSdkError.UnsupportedCurve::class.java)
    }

    @Test
    fun performPreCheck_derivationWhenHdWalletDisabled_returnsHdWalletDisabled() {
        val error = task(derivationPath = DerivationPath(rawPath = "m/0/1"))
            .invokePerformPreCheck(preCheckCardMock(isHdWalletAllowed = false))

        assertThat(error).isInstanceOf(TangemSdkError.HDWalletDisabled::class.java)
    }

    @Test
    fun performPreCheck_derivationWithAllConditionsMet_passes() {
        val error = task(derivationPath = DerivationPath(rawPath = "m/0/1"))
            .invokePerformPreCheck(preCheckCardMock())

        assertThat(error).isNull()
    }

    // endregion

    // region serialize

    @Test
    fun serialize_alwaysAppendsCardIdChallengeAndWalletIndex() {
        val apdu = task().serialize(environmentMock(walletIndex = 3))

        val tlvs = apdu.parseTlvs()
        assertThat(tlvs.map { it.tag }).containsAtLeast(TlvTag.CardId, TlvTag.Challenge, TlvTag.WalletIndex)
        assertThat(tlvs.first { it.tag == TlvTag.Challenge }.value).isEqualTo(challenge)
    }

    @Test
    fun serialize_dynamicModeOnModernFirmware_appendsPublicKeyChallengeWithChallenge() {
        val apdu = task(confirmationMode = AttestWalletKeyTask.ConfirmationMode.Dynamic)
            .serialize(environmentMock(firmwareVersion = FirmwareVersion(2, 1)))

        val publicKeyChallenge = apdu.parseTlvs().firstOrNull { it.tag == TlvTag.PublicKeyChallenge }
        assertThat(publicKeyChallenge).isNotNull()
        assertThat(publicKeyChallenge!!.value).isEqualTo(challenge)
    }

    @Test
    fun serialize_staticModeOnModernFirmware_appendsEmptyPublicKeyChallenge() {
        val apdu = task(confirmationMode = AttestWalletKeyTask.ConfirmationMode.Static)
            .serialize(environmentMock(firmwareVersion = FirmwareVersion(2, 1)))

        val publicKeyChallenge = apdu.parseTlvs().firstOrNull { it.tag == TlvTag.PublicKeyChallenge }
        assertThat(publicKeyChallenge).isNotNull()
        assertThat(publicKeyChallenge!!.value).isEmpty()
    }

    @Test
    fun serialize_noneModeOnModernFirmware_omitsPublicKeyChallenge() {
        val apdu = task(confirmationMode = AttestWalletKeyTask.ConfirmationMode.None)
            .serialize(environmentMock(firmwareVersion = FirmwareVersion(2, 1)))

        assertThat(apdu.parseTlvs().map { it.tag }).doesNotContain(TlvTag.PublicKeyChallenge)
    }

    @Test
    fun serialize_onLegacyFirmware_omitsPublicKeyChallengeEvenInDynamicMode() {
        // COS < 2.1 does not support wallet-ownership confirmation, so the tag must never be sent.
        val apdu = task(confirmationMode = AttestWalletKeyTask.ConfirmationMode.Dynamic)
            .serialize(environmentMock(firmwareVersion = FirmwareVersion(1, 0)))

        assertThat(apdu.parseTlvs().map { it.tag }).doesNotContain(TlvTag.PublicKeyChallenge)
    }

    @Test
    fun serialize_withDerivationPath_appendsWalletHdPath() {
        val apdu = task(derivationPath = DerivationPath(rawPath = "m/0/1"))
            .serialize(environmentMock())

        assertThat(apdu.parseTlvs().map { it.tag }).contains(TlvTag.WalletHDPath)
    }

    @Test
    fun serialize_withoutDerivationPath_omitsWalletHdPath() {
        val apdu = task(derivationPath = null).serialize(environmentMock())

        assertThat(apdu.parseTlvs().map { it.tag }).doesNotContain(TlvTag.WalletHDPath)
    }

    // endregion

    // region deserialize

    @Test
    fun deserialize_withAllFields_mapsEveryField() {
        val cardSignature = ByteArray(SIGNATURE_SIZE) { 5 }
        val response = task().deserialize(
            environment = mockk(relaxed = true),
            apdu = responseApdu(
                cardSignature = cardSignature,
                publicKeySalt = publicKeySalt,
                walletStatus = CardWallet.Status.Loaded,
                counter = 42,
            ),
        )

        assertThat(response.salt).isEqualTo(salt)
        assertThat(response.cardSignature).isEqualTo(cardSignature)
        assertThat(response.publicKeySalt).isEqualTo(publicKeySalt)
        assertThat(response.walletStatus).isEqualTo(CardWallet.Status.Loaded)
        assertThat(response.counter).isEqualTo(42)
    }

    @Test
    fun deserialize_withoutOptionalCounter_returnsNullCounterWithoutThrowing() {
        // Regression: `counter` is COS 2.01+. A missing CheckWalletCounter TLV must decode to null
        // rather than throwing DecodingFailedMissingTag and failing the whole attestation.
        val response = task().deserialize(
            environment = mockk(relaxed = true),
            apdu = responseApdu(counter = null),
        )

        assertThat(response.counter).isNull()
    }

    @Test
    fun deserialize_withoutOptionalCardSignatureAndSalt_returnsNulls() {
        val response = task().deserialize(
            environment = mockk(relaxed = true),
            apdu = responseApdu(cardSignature = null, publicKeySalt = null, walletStatus = null),
        )

        assertThat(response.cardSignature).isNull()
        assertThat(response.publicKeySalt).isNull()
        assertThat(response.walletStatus).isNull()
    }

    @Test
    fun deserialize_populatesChallengeFromTask() {
        val response = task().deserialize(environment = mockk(relaxed = true), apdu = responseApdu())

        assertThat(response.challenge).isEqualTo(challenge)
    }

    // endregion

    // region helpers

    private fun task(
        publicKey: ByteArray = walletPublicKey,
        derivationPath: DerivationPath? = null,
        confirmationMode: AttestWalletKeyTask.ConfirmationMode = AttestWalletKeyTask.ConfirmationMode.Dynamic,
    ) = AttestWalletKeyTask(
        publicKey = publicKey,
        derivationPath = derivationPath,
        challenge = challenge,
        confirmationMode = confirmationMode,
    )

    private fun response(
        walletSignature: ByteArray,
        cardSignature: ByteArray? = null,
        publicKeySalt: ByteArray? = null,
        walletStatus: CardWallet.Status? = null,
    ) = AttestWalletKeyResponse(
        cardId = "c000111122223333",
        salt = salt,
        walletSignature = walletSignature,
        challenge = challenge,
        cardSignature = cardSignature,
        publicKeySalt = publicKeySalt,
        walletStatus = walletStatus,
        counter = null,
    )

    private fun wallet(
        publicKey: ByteArray = walletPublicKey,
        curve: EllipticCurve = this.curve,
        index: Int = 0,
        derivedKeys: Map<DerivationPath, ExtendedPublicKey> = emptyMap(),
    ) = CardWallet(
        publicKey = publicKey,
        chainCode = null,
        curve = curve,
        settings = CardWallet.Settings(isPermanent = true),
        totalSignedHashes = 0,
        remainingSignatures = null,
        index = index,
        isImported = false,
        hasBackup = false,
        derivedKeys = derivedKeys,
    )

    private fun preCheckCardMock(
        firmwareVersion: FirmwareVersion = FirmwareVersion(6, 0),
        isHdWalletAllowed: Boolean = true,
        wallet: CardWallet? = wallet(),
    ): Card {
        val settings = mockk<Card.Settings>()
        every { settings.isHDWalletAllowed } returns isHdWalletAllowed

        val card = mockk<Card>()
        every { card.wallet(any()) } returns wallet
        every { card.firmwareVersion } returns firmwareVersion
        every { card.settings } returns settings
        return card
    }

    private fun environmentMock(
        firmwareVersion: FirmwareVersion = FirmwareVersion(2, 1),
        walletIndex: Int = 0,
    ): SessionEnvironment {
        val card = mockk<Card>()
        every { card.cardId } returns "c000111122223333"
        every { card.firmwareVersion } returns firmwareVersion
        every { card.wallet(any()) } returns wallet(index = walletIndex)

        val environment = mockk<SessionEnvironment>()
        every { environment.card } returns card
        every { environment.accessCode } returns UserCode(UserCodeType.AccessCode)
        return environment
    }

    private fun responseApdu(
        cardSignature: ByteArray? = null,
        publicKeySalt: ByteArray? = null,
        walletStatus: CardWallet.Status? = null,
        counter: Int? = null,
    ): ResponseApdu {
        val builder = TlvBuilder()
        builder.append(TlvTag.CardId, "c000111122223333")
        builder.append(TlvTag.Salt, salt)
        builder.append(TlvTag.WalletSignature, ByteArray(SIGNATURE_SIZE) { 1 })
        builder.append(TlvTag.CardSignature, cardSignature)
        builder.append(TlvTag.PublicKeySalt, publicKeySalt)
        builder.append(TlvTag.Status, walletStatus)
        builder.append(TlvTag.CheckWalletCounter, counter)
        // Append the "process completed" (0x9000) status word that ResponseApdu strips off.
        return ResponseApdu(builder.serialize() + byteArrayOf(0x90.toByte(), 0x00))
    }

    private fun CommandApdu.parseTlvs(): List<Tlv> =
        Tlv.deserialize(apduData.copyOfRange(APDU_HEADER_SIZE, apduData.size)).orEmpty()

    private fun cardMock(firmwareVersion: FirmwareVersion = FirmwareVersion(2, 1)): Card {
        val card = mockk<Card>()
        every { card.cardPublicKey } returns cardPublicKey
        every { card.firmwareVersion } returns firmwareVersion
        return card
    }

    private fun AttestWalletKeyTask.invokePerformPreCheck(card: Card): TangemError? {
        val method = AttestWalletKeyTask::class.java
            .getDeclaredMethod("performPreCheck", Card::class.java)
            .apply { isAccessible = true }
        return try {
            method.invoke(this, card) as? TangemError
        } catch (error: InvocationTargetException) {
            throw error.cause ?: error
        }
    }

    private fun AttestWalletKeyTask.invokeVerifyWalletSignature(
        response: AttestWalletKeyResponse,
        wallet: CardWallet,
    ): Boolean = invokePrivate(
        "verifyWalletSignature",
        AttestWalletKeyResponse::class.java to response,
        CardWallet::class.java to wallet,
    )

    private fun AttestWalletKeyTask.invokeVerifyCardSignature(response: AttestWalletKeyResponse, card: Card): Boolean =
        invokePrivate(
            "verifyCardSignature",
            AttestWalletKeyResponse::class.java to response,
            Card::class.java to card,
        )

    private fun AttestWalletKeyTask.invokePrivate(name: String, vararg args: Pair<Class<*>, Any>): Boolean {
        val method = AttestWalletKeyTask::class.java
            .getDeclaredMethod(name, *args.map { it.first }.toTypedArray())
            .apply { isAccessible = true }
        return try {
            method.invoke(this, *args.map { it.second }.toTypedArray()) as Boolean
        } catch (error: InvocationTargetException) {
            throw error.cause ?: error
        }
    }

    // endregion

    private companion object {
        const val CHALLENGE_SIZE = 16
        const val SALT_SIZE = 16
        const val KEY_SIZE = 32
        const val SIGNATURE_SIZE = 64

        // CommandApdu header before the TLV payload: cla + ins + p1 + p2 + 3-byte length.
        const val APDU_HEADER_SIZE = 7
    }
}
package com.tangem

import com.tangem.common.CompletionResult
import com.tangem.common.SuccessResponse
import com.tangem.common.UserCode
import com.tangem.common.UserCodeType
import com.tangem.common.authentication.AuthenticationManager
import com.tangem.common.authentication.DummyAuthenticationManager
import com.tangem.common.authentication.keystore.DummyKeystoreManager
import com.tangem.common.authentication.keystore.KeystoreManager
import com.tangem.common.card.Card
import com.tangem.common.card.EllipticCurve
import com.tangem.common.core.*
import com.tangem.common.json.*
import com.tangem.common.nfc.CardReader
import com.tangem.common.nfc.NfcAvailabilityProvider
import com.tangem.common.services.Result
import com.tangem.common.services.secure.SecureStorage
import com.tangem.common.services.toTangemSdkError
import com.tangem.common.usersCode.UserCodeRepository
import com.tangem.crypto.CryptoUtils
import com.tangem.crypto.bip39.DefaultMnemonic
import com.tangem.crypto.bip39.Wordlist
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.crypto.hdWallet.bip32.ExtendedPublicKey
import com.tangem.crypto.hdWallet.masterkey.AnyMasterKeyFactory
import com.tangem.operations.*
import com.tangem.operations.attestation.AttestCardKeyCommand
import com.tangem.operations.attestation.AttestCardKeyResponse
import com.tangem.operations.attestation.OnlineCardVerifier
import com.tangem.operations.attestation.api.models.CardVerifyAndGetInfo
import com.tangem.operations.derivation.DeriveWalletPublicKeyTask
import com.tangem.operations.derivation.DeriveWalletPublicKeysTask
import com.tangem.operations.derivation.ExtendedPublicKeysMap
import com.tangem.operations.files.*
import com.tangem.operations.issuerAndUserData.*
import com.tangem.operations.personalization.DepersonalizeCommand
import com.tangem.operations.personalization.DepersonalizeResponse
import com.tangem.operations.personalization.PersonalizeCommand
import com.tangem.operations.personalization.entities.*
import com.tangem.operations.pins.SetUserCodeCommand
import com.tangem.operations.preflightread.CardIdPreflightReadFilter
import com.tangem.operations.preflightread.PreflightReadFilter
import com.tangem.operations.sign.*
import com.tangem.operations.usersetttings.SetUserCodeRecoveryAllowedTask
import com.tangem.operations.wallet.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow

/**
 * The main interface of Tangem SDK that allows your app to communicate with Tangem cards.
 *
 * @property reader is an interface that is responsible for NFC connection and
 * transfer of data to and from the Tangem Card.
 * Its default implementation, NfcCardReader, is in our tangem-sdk module.
 * @property viewDelegate An interface that allows interaction with users and shows relevant UI.
 * Its default implementation, DefaultCardSessionViewDelegate, is in our tangem-sdk module.
 * @property config allows to change a number of parameters for communication with Tangem cards.
 * Do not change the default values unless you know what you are doing.
 */
@Suppress("LargeClass", "TooManyFunctions")
class TangemSdk(
    private val reader: CardReader,
    private val viewDelegate: SessionViewDelegate,
    private val nfcAvailabilityProvider: NfcAvailabilityProvider,
    val secureStorage: SecureStorage,
    val wordlist: Wordlist,
    var config: Config = Config(),
    val authenticationManager: AuthenticationManager = DummyAuthenticationManager(),
    val keystoreManager: KeystoreManager = DummyKeystoreManager(),
) {

    private var cardSession: CardSession? = null

    private val onlineCardVerifier = OnlineCardVerifier()
    private val jsonRpcConverter: JSONRPCConverter by lazy { JSONRPCConverter.shared(wordlist) }

    init {
        CryptoUtils.initCrypto()
    }

    /**
     * This method enables [NfcAdapter] reader mode if it was disabled
     * Use carefully, by default sdk enables reader mode automatically
     * but some devices could disable it in some cases out of lifecycle
     * For example: launch qr code scanner on Samsung
     */
    fun forceEnableReaderMode() {
        reader.forceEnableReaderMode()
    }

    /**
     * This method disables [NfcAdapter] reader mode if it was enabled
     * Don't forget call [forceEnableReaderMode] after this
     */
    fun forceDisableReaderMode() {
        reader.forceDisableReaderMode()
    }

    /**
     * Returns a [StateFlow] that indicates whether any TangemSdk UI is currently visible
     */
    fun uiVisibility(): StateFlow<Boolean> {
        return viewDelegate.viewVisibility
    }

    // region Card operations
    /**
     * This method launches a [ScanTask] on a new thread.
     *
     * To start using any card, you first need to read it using the scanCard() method.
     * This method launches an NFC session, and once it’s connected with the card,
     * it obtains the card data. Optionally, if the card contains a wallet (private and public key pair),
     * it proves that the wallet owns a private key that corresponds to a public one.
     *
     * @param initialMessage A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used.
     * @param allowRequestUserCodeFromRepository Allow access code or password request from [UserCodeRepository],
     * If [Config.userCodeRequestPolicy] is set to [UserCodeRequestPolicy.AlwaysWithBiometrics], the code will still be saved
     * @param callback is triggered on the completion of the [ScanTask] and provides card response
     * in the form of [Card] if the task was performed successfully or [TangemSdkError] in case of an error.
     */
    fun scanCard(
        initialMessage: Message? = null,
        allowRequestUserCodeFromRepository: Boolean = false,
        callback: CompletionCallback<Card>,
    ) {
        startSessionWithRunnable(
            runnable = ScanTask(allowRequestUserCodeFromRepository),
            cardId = null,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * This method launches a [AttestCardKeyCommand] on a new thread.
     *
     * To start using any card, you first need to read it using the scanCard() method.
     * This method launches an NFC session, and once it’s connected with the card,
     * it obtains the card data. Optionally, if the card contains a wallet (private and public key pair),
     * it proves that the wallet owns a private key that corresponds to a public one.
     *
     * @param attestationMode Attestation mode to use. Full attestation available only for COS v6+.
     * It can be used to get all public keys of linked cards.
     * @param challenge Optional challenge. If null, it will be created automatically and returned in command response.
     * @param cardId CID, Unique Tangem card ID number.
     * @param initialMessage A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used.
     * @param callback is triggered on the completion of the [ScanTask] and provides card response.
     * in the form of [Card] if the task was performed successfully or [TangemSdkError] in case of an error.
     */
    fun attestCardKey(
        attestationMode: AttestCardKeyCommand.Mode = AttestCardKeyCommand.Mode.Default,
        challenge: ByteArray? = null,
        cardId: String? = null,
        initialMessage: Message? = null,
        callback: CompletionCallback<AttestCardKeyResponse>,
    ) {
        startSessionWithRunnable(
            runnable = AttestCardKeyCommand(mode = attestationMode, challenge = challenge),
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * This method allows you to sign one hash and will return a corresponding signature.
     * Please note that Tangem cards usually protect the signing with a security delay
     * that may last up to 45 seconds, depending on a card.
     * It is for `SessionViewDelegate` to notify users of security delay.
     *
     * @param hash: Transaction hash for sign by card.
     * @param walletPublicKey: Public key of wallet that should sign hash.
     * @param cardId: CID, Unique Tangem card ID number
     * @param derivationPath: Derivation path of the wallet. Optional
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * default message will be used
     * @param callback: is triggered on the completion of the [SignCommand] and provides response
     * in the form of single signed hash [ByteArray] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     */
    fun sign(
        hash: ByteArray,
        walletPublicKey: ByteArray,
        cardId: String?,
        derivationPath: DerivationPath? = null,
        initialMessage: Message? = null,
        callback: CompletionCallback<SignHashResponse>,
    ) {
        val command = SignHashCommand(hash, walletPublicKey, derivationPath)
        startSessionWithRunnable(
            runnable = command,
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * This method launches a [SignCommand] on a new thread.
     *
     * It allows you to sign one or multiple hashes.
     * Simultaneous signing of array of hashes in a single [SignCommand] is required to support
     * Bitcoin-type multi-input blockchains (UTXO).
     * The [SignCommand] will return a corresponding array of signatures.
     *
     * Please note that Tangem cards usually protect the signing with a security delay
     * that may last up to 45 seconds, depending on a card.
     * It is for [SessionViewDelegate] to notify users of security delay.
     *
     * @param hashes: Array of transaction hashes. It can be from one or up to ten hashes of the same length.
     * @param walletPublicKey: Public key of the wallet that should sign hashes.
     * @param cardId: CID, Unique Tangem card ID number
     * @param derivationPath: Derivation path of the wallet. Optional
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used.
     * @param callback: is triggered on the completion of the [SignCommand] and provides response
     * in the form of list of signed hashes [List<ByteArray>] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     */
    fun sign(
        hashes: Array<ByteArray>,
        walletPublicKey: ByteArray,
        cardId: String?,
        derivationPath: DerivationPath? = null,
        initialMessage: Message? = null,
        callback: CompletionCallback<SignResponse>,
    ) {
        val command = SignCommand(hashes, walletPublicKey, derivationPath)
        startSessionWithRunnable(
            runnable = command,
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * This method launches a [SignCommand] on a new thread.
     *
     * It allows you to sign one or multiple hashes.
     * Simultaneous signing of array of hashes in a single [SignCommand] is required to support
     * Bitcoin-type multi-input blockchains (UTXO).
     * The [SignCommand] will return a corresponding array of signatures.
     *
     * Please note that Tangem cards usually protect the signing with a security delay
     * that may last up to 45 seconds, depending on a card.
     * It is for [SessionViewDelegate] to notify users of security delay.
     *
     * @param dataToSign: Array of transaction hashes. It can be from one or up to ten hashes of the same length.
     * @param walletPublicKey: Public key of the wallet that should sign hashes.
     * @param cardId: CID, Unique Tangem card ID number
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used.
     * @param callback: is triggered on the completion of the [SignCommand] and provides response
     * in the form of list of signed hashes [List<ByteArray>] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     */
    fun sign(
        dataToSign: List<SignData>,
        walletPublicKey: ByteArray,
        cardId: String?,
        initialMessage: Message? = null,
        callback: CompletionCallback<List<SignHashResponse>>,
    ) {
        val command = MultipleSignCommand(dataToSign, walletPublicKey)
        startSessionWithRunnable(
            runnable = command,
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * Derive public key according to BIP32 (Private parent key → public child key)
     * Warning: Only `secp256k1` and `ed25519` (BIP32-Ed25519 scheme) curves supported
     *
     * @param cardId: CID, Unique Tangem card ID number.
     * @param walletPublicKey: Seed public key.
     * @param derivationPath: Derivation path
     * @param initialMessage: A custom description that shows at the beginning of the NFC session. If null, default
     * message will be used
     * @param callback: is triggered on the completion of the [DeriveWalletPublicKeyTask] and provides response
     * in the form of the [ExtendedPublicKey] if the task was performed successfully or [TangemSdkError] in case
     * of an error.
     */
    fun deriveWalletPublicKey(
        cardId: String,
        walletPublicKey: ByteArray,
        derivationPath: DerivationPath,
        initialMessage: Message? = null,
        callback: CompletionCallback<ExtendedPublicKey>,
    ) {
        val command = DeriveWalletPublicKeyTask(walletPublicKey, derivationPath)
        startSessionWithRunnable(
            runnable = command,
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * Derive multiple wallet public keys according to BIP32 (Private parent key → public child key)
     * Warning: Only `secp256k1` and `ed25519` (BIP32-Ed25519 scheme) curves supported
     *
     * @param cardId: CID, Unique Tangem card ID number.
     * @param walletPublicKey: Seed public key.
     * @param derivationPaths: Derivation paths. Repeated items will be ignored.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session. If null, default
     * message will be used
     * @param callback: is triggered on the completion of the [DeriveWalletPublicKeyTask] and provides response
     * in the form of the [ExtendedPublicKeyList] if the task was performed successfully or [TangemSdkError] in case
     * of an error. All derived keys are unique and will be returned in arbitrary order.
     */
    fun deriveWalletPublicKeys(
        cardId: String,
        walletPublicKey: ByteArray,
        derivationPaths: List<DerivationPath>,
        initialMessage: Message? = null,
        callback: CompletionCallback<ExtendedPublicKeysMap>,
    ) {
        val command = DeriveWalletPublicKeysTask(walletPublicKey, derivationPaths)
        startSessionWithRunnable(
            runnable = command,
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * This method launches a [CreateWalletTask] on a new thread.
     *
     * This command will create a new wallet on the card having ‘Empty’ state.
     * A key pair WalletPublicKey / WalletPrivateKey is generated and securely stored in the card.
     * App will need to obtain Wallet_PublicKey from the response of [CreateWalletTask] or [ReadCommand]
     * and then transform it into an address of corresponding blockchain wallet  according to a specific
     * blockchain algorithm.
     * WalletPrivateKey is never revealed by the card and will be used by [SignCommand] and [AttestWalletKeyCommand].
     * RemainingSignature is set to MaxSignatures.
     *
     * @param curve: Wallet's elliptic curve
     * @param cardId: CID, Unique Tangem card ID number.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used
     * @param callback: is triggered on the completion of the [CreateWalletTask] and provides
     * card response in the form of [CreateWalletResponse] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     */
    fun createWallet(
        curve: EllipticCurve,
        cardId: String,
        initialMessage: Message? = null,
        callback: CompletionCallback<CreateWalletResponse>,
    ) {
        startSessionWithRunnable(
            runnable = CreateWalletTask(curve),
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * This command will import an existing wallet.
     *
     * @param curve: Wallet's elliptic curve
     * @param mnemonic: BIP39 mnemonic to create wallet from. COS v.6+.
     * @param passphrase: BIP39 passphrase to create wallet from. COS v.6+. Empty passphrase by default.
     * @param cardId: CID, Unique Tangem card ID number.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used
     * @param callback: is triggered on the completion of the [CreateWalletTask] and provides
     * card response in the form of [CreateWalletResponse] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     */
    fun importWallet(
        curve: EllipticCurve,
        cardId: String,
        mnemonic: String,
        passphrase: String = "",
        initialMessage: Message? = null,
        callback: CompletionCallback<CreateWalletResponse>,
    ) {
        try {
            val defaultMnemonic = DefaultMnemonic(mnemonic, wordlist)
            val factory = AnyMasterKeyFactory(defaultMnemonic, passphrase)
            val privateKey = factory.makeMasterKey(curve)

            startSessionWithRunnable(
                runnable = CreateWalletTask(curve, privateKey),
                cardId = cardId,
                initialMessage = initialMessage,
                accessCode = null,
                callback = callback,
            )
        } catch (error: Exception) {
            callback(CompletionResult.Failure(error.toTangemSdkError()))
        }
    }

    /**
     * This method launches a [PurgeWalletCommand] on a new thread.
     *
     * This command deletes all wallet data. If IsReusable flag is enabled during personalization.
     *
     * @param walletPublicKey: Public key of wallet that should be purged.
     * @param cardId: CID, Unique Tangem card ID number.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used.
     * @param callback: is triggered on the completion of the [PurgeWalletCommand] and provides
     * card response in the form of [SuccessResponse] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     */
    fun purgeWallet(
        walletPublicKey: ByteArray,
        cardId: String,
        initialMessage: Message? = null,
        callback: CompletionCallback<SuccessResponse>,
    ) {
        startSessionWithRunnable(
            runnable = PurgeWalletCommand(walletPublicKey),
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     *  Get the card info and verify with Tangem backend. Do not use for developer cards
     *
     *  @param cardPublicKey: CardPublicKey returned by [ReadCommand]
     *  @param cardId: CID, Unique Tangem card ID number.
     *  @param callback: [CardVerifyAndGetInfo.Response.Item]
     */
    fun loadCardInfo(
        cardPublicKey: ByteArray,
        cardId: String,
        callback: CompletionCallback<CardVerifyAndGetInfo.Response.Item>,
    ) {
        onlineCardVerifier.scope.launch {
            when (val result = onlineCardVerifier.getCardInfo(cardId, cardPublicKey)) {
                is Result.Success -> callback(CompletionResult.Success(result.data))
                is Result.Failure -> callback(CompletionResult.Failure(result.toTangemSdkError()))
            }
        }
    }

    /**
     * This method launches a [PersonalizeCommand] on a new thread.
     *
     * Command available on SDK cards only
     * Personalization is an initialization procedure, required before starting using a card.
     * During this procedure a card setting is set up.
     * During this procedure all data exchange is encrypted.
     *
     * @param config: is a configuration file with all the card settings that are written on the card
     * during personalization.
     * @param issuer: Issuer is a third-party team or company wishing to use Tangem cards.
     * @param manufacturer: Tangem Card Manufacturer.
     * @param acquirer: Acquirer is a trusted third-party company that operates proprietary
     * (non-EMV) POS terminal infrastructure and transaction processing back-end.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used.
     * @param callback: is triggered on the completion of the [PersonalizeCommand] and provides
     * card response in the form of [Card] if the command was performed successfully
     * or [TangemSdkError] in case of an error.
     */
    fun personalize(
        config: CardConfig,
        issuer: Issuer,
        manufacturer: Manufacturer,
        acquirer: Acquirer? = null,
        initialMessage: Message? = null,
        callback: CompletionCallback<Card>,
    ) {
        val command = PersonalizeCommand(config, issuer, manufacturer, acquirer)
        startSessionWithRunnable(
            runnable = command,
            cardId = null,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * This method launches a [DepersonalizeCommand] on a new thread.
     *
     * Command available on SDK cards only
     * This command resets card to initial state, erasing all data written during personalization and usage.
     *
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used.
     * @param callback: is triggered on the completion of the [DepersonalizeCommand] and provides
     * card response in the form of [DepersonalizeResponse] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     * */
    fun depersonalize(initialMessage: Message? = null, callback: CompletionCallback<DepersonalizeResponse>) {
        startSessionWithRunnable(
            runnable = DepersonalizeCommand(),
            cardId = null,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * This method launches a [SetUserCodeCommand] on a new thread.
     *
     * Set or change card's access code
     *
     * @param accessCode: Access code to set. If null, the user will be prompted to enter code before operation
     * @param cardId: CID, Unique Tangem card ID number.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used.
     * @param callback: is triggered on the completion of the [SetUserCodeCommand] and provides
     * card response in the form of [SuccessResponse] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     * */
    fun setAccessCode(
        accessCode: String? = null,
        cardId: String,
        initialMessage: Message? = null,
        callback: CompletionCallback<SuccessResponse>,
    ) {
        val command = SetUserCodeCommand.changeAccessCode(accessCode)
        startSessionWithRunnable(
            runnable = command,
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * This method launches a [SetUserCodeCommand] on a new thread.
     *
     * Set or change card's passcode
     *
     * @param passcode: Passcode to set. If null, the user will be prompted to enter code before operation
     * @param cardId: CID, Unique Tangem card ID number.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used.
     * @param callback: is triggered on the completion of the [SetUserCodeCommand] and provides
     * card response in the form of [SuccessResponse] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     * */
    fun setPasscode(
        passcode: String? = null,
        cardId: String,
        initialMessage: Message? = null,
        callback: CompletionCallback<SuccessResponse>,
    ) {
        val command = SetUserCodeCommand.changePasscode(passcode)
        startSessionWithRunnable(
            runnable = command,
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * This method launches a [SetUserCodeCommand] on a new thread.
     *
     * Reset all user codes
     *
     * @param cardId: CID, Unique Tangem card ID number.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used
     * @param callback: is triggered on the completion of the [SetUserCodeCommand] and provides
     * card response in the form of [SuccessResponse] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     */
    fun resetUserCodes(
        cardId: String,
        initialMessage: Message? = null,
        callback: CompletionCallback<SuccessResponse>,
    ) {
        startSessionWithRunnable(
            runnable = SetUserCodeCommand.resetUserCodes(),
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * This method launches a [SetUserCodeRecoveryAllowedTask] on a new thread.
     *
     * Set if card allowed to reset user code
     *
     * @param isAllowed: Whether this card can reset user codes on the other linked card or not
     * @param cardId: CID, Unique Tangem card ID number.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used
     * @param callback: is triggered on the completion of the [SetUserCodeRecoveryAllowedTask] and provides
     * card response in the form of [SuccessResponse] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     */
    fun setUserCodeRecoveryAllowed(
        isAllowed: Boolean,
        cardId: String,
        initialMessage: Message? = null,
        callback: CompletionCallback<SuccessResponse>,
    ) {
        startSessionWithRunnable(
            runnable = SetUserCodeRecoveryAllowedTask(isAllowed),
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * This method launches a [ReadFilesTask] on a new thread.
     *
     * This command reads all files stored on card.
     * Note: When performing reading private files command, you must  provide `passcode`
     * Warning: Command available only for cards with COS 3.29 and higher
     *
     * @param readPrivateFiles: If true - all files saved on card will be read. User code or security delay will be
     * requested
     * @param fileName: Read files by the given name.
     * @param walletPublicKey: Read files by the given wallet.
     * @param cardId: CID, Unique Tangem card ID number.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used
     * @param callback: is triggered on the completion of the [ReadFilesTask] and provides
     * card response in the form of [List<File>] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     */
    fun readFiles(
        readPrivateFiles: Boolean = false,
        fileName: String? = null,
        walletPublicKey: ByteArray? = null,
        cardId: String? = null,
        initialMessage: Message? = null,
        callback: CompletionCallback<List<File>>,
    ) {
        val task = ReadFilesTask(fileName, walletPublicKey)
        task.shouldReadPrivateFiles = readPrivateFiles
        startSessionWithRunnable(
            runnable = task,
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * This method launches a [ChangeFileSettingsTask] on a new thread.
     *
     * Updates selected file settings provided within [File].
     * To perform file settings update you should initially read all files (`readFiles` command), select files
     * that you want to update, change their settings in [FileSettings] and add them to `files` array.
     * Note: In COS 3.29 and higher only file visibility option (public or private) available to update
     * Warning: This method works with COS 3.29 and higher
     *
     * @param changes: Dictionary of file indices with new settings
     * @param cardId: CID, Unique Tangem card ID number.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used.
     * @param callback: is triggered on the completion of the [ChangeFileSettingsTask] and provides
     * card response in the form of [SuccessResponse] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     */
    fun changeFileSettings(
        changes: Map<Int, FileVisibility>,
        cardId: String? = null,
        initialMessage: Message? = null,
        callback: CompletionCallback<SuccessResponse>,
    ) {
        val task = ChangeFileSettingsTask(changes)
        startSessionWithRunnable(
            runnable = task,
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * This method launches a [WriteFilesTask] on a new thread.
     *
     * This command write all files provided in `files` to card.
     * Warning: This command available for COS 3.29 and higher
     * Note: Writing files protected by Passcode only available for COS 3.34 and higher
     *
     * @param files: List of files that should be written to card
     * @param cardId: CID, Unique Tangem card ID number.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used.
     * @param callback: is triggered on the completion of the [WriteFilesTask] and provides
     * card response in the form of [WriteFileResponse] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     */
    fun writeFiles(
        files: List<FileToWrite>,
        cardId: String? = null,
        initialMessage: Message? = null,
        callback: CompletionCallback<WriteFilesResponse>,
    ) {
        startSessionWithRunnable(
            runnable = WriteFilesTask(files),
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * This method launches a [DeleteFilesTask] on a new thread.
     *
     * This command deletes selected files from card. This operation can't be undone.
     * To perform file deletion you should initially read all files (`readFiles` command) and add them to
     * `indices` array. When files deleted from card, other files change their indices.
     * After deleting files you should additionally perform `readFiles` command to actualize files indices
     * Warning: This command available for COS 3.29 and higher
     *
     * @param indices optional array of indices that should be deleted. If not specified all files
     * will be deleted from card
     * @param cardId: CID, Unique Tangem card ID number.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used.
     * @param callback: is triggered on the completion of the [DeleteFilesTask] and provides
     * card response in the form of [DeleteFileResponse] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     */
    fun deleteFiles(
        indices: List<Int>? = null,
        cardId: String? = null,
        initialMessage: Message? = null,
        callback: CompletionCallback<SuccessResponse>,
    ) {
        startSessionWithRunnable(
            runnable = DeleteFilesTask(indices),
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * Creates hashes and signatures for files that signed by issuer
     *
     * @param cardId: CID, Unique Tangem card ID number.
     * @param fileData: File data that will be written on card
     * @param fileCounter:  A counter that protects issuer data against replay attack.
     * @param fileName: Optional name of the file.
     * @param privateKey: Optional private key that will be used for signing files hashes.
     * If it is provided, then  `FileHashData` will contain signed file signatures.
     * @return [FileHashData] with hashes to sign and signatures if [privateKey] was provided.
     */
    fun prepareHashes(
        cardId: String,
        fileData: ByteArray,
        fileCounter: Int,
        fileName: String? = null,
        privateKey: ByteArray? = null,
    ): FileHashData {
        return FileHashHelper.prepareHashes(cardId, fileData, fileCounter, fileName, privateKey)
    }

    /**
     * This method launches a [ReadIssuerDataCommand] on a new thread.
     *
     * This command returns 512-byte Issuer Data field and its issuer’s signature.
     * Issuer Data is never changed or parsed from within the Tangem COS. The issuer defines purpose of use,
     * format and payload of Issuer Data. For example, this field may contain information about
     * wallet balance signed by the issuer or additional issuer’s attestation data.
     *
     * @param cardId: CID, Unique Tangem card ID number.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used.
     * @param callback: is triggered on the completion of the [ReadIssuerDataCommand] and provides
     * card response in the form of [ReadIssuerDataResponse] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     */
    @Deprecated(message = "Use files instead")
    fun readIssuerData(
        cardId: String? = null,
        initialMessage: Message? = null,
        callback: CompletionCallback<ReadIssuerDataResponse>,
    ) {
        val command = ReadIssuerDataCommand(config.issuerPublicKey)
        startSessionWithRunnable(
            runnable = command,
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * This method launches a [WriteIssuerDataCommand] on a new thread.
     *
     * This command writes 512-byte Issuer Data field and its issuer’s signature.
     * Issuer Data is never changed or parsed from within the Tangem COS. The issuer defines purpose of use,
     * format and payload of Issuer Data. For example, this field may contain information about
     * wallet balance signed by the issuer or additional issuer’s attestation data.
     *
     * @param cardId: CID, Unique Tangem card ID number.
     * @param issuerData: Data provided by issuer.
     * @param issuerDataSignature: Issuer’s signature of [issuerData] with Issuer Data Private Key.
     * @param issuerDataCounter: An optional counter that protect issuer data against replay attack.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used.
     * @param callback: is triggered on the completion of the [WriteIssuerDataCommand] and provides
     * card response in the form of [WriteIssuerDataResponse] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     */
    @Deprecated(message = "Use files instead")
    fun writeIssuerData(
        cardId: String? = null,
        issuerData: ByteArray,
        issuerDataSignature: ByteArray,
        issuerDataCounter: Int? = null,
        initialMessage: Message? = null,
        callback: CompletionCallback<SuccessResponse>,
    ) {
        val command = WriteIssuerDataCommand(
            issuerData = issuerData,
            issuerDataSignature = issuerDataSignature,
            issuerDataCounter = issuerDataCounter,
            issuerPublicKey = config.issuerPublicKey,
        )
        startSessionWithRunnable(
            runnable = command,
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * This method launches a [ReadIssuerExtraDataCommand] on a new thread.
     *
     * This command retrieves Issuer Extra Data field and its issuer’s signature.
     * Issuer Extra Data is never changed or parsed from within the Tangem COS. The issuer defines purpose of use,
     * format and payload of Issuer Data. . For example, this field may contain photo or
     * biometric information for ID card product. Because of the large size of Issuer_Extra_Data,
     * a series of these commands have to be executed to read the entire Issuer_Extra_Data.
     *
     * @param cardId: CID, Unique Tangem card ID number.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used.
     * @param callback: is triggered on the completion of the [ReadIssuerExtraDataCommand] and provides
     * card response in the form of [ReadIssuerExtraDataResponse] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     */
    @Deprecated(message = "Use files instead")
    fun readIssuerExtraData(
        cardId: String? = null,
        initialMessage: Message? = null,
        callback: CompletionCallback<ReadIssuerExtraDataResponse>,
    ) {
        val command = ReadIssuerExtraDataCommand(config.issuerPublicKey)
        startSessionWithRunnable(
            runnable = command,
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * This method launches a [WriteIssuerExtraDataCommand] on a new thread.
     *
     * This command writes Issuer Extra Data field and its issuer’s signature.
     * Issuer Extra Data is never changed or parsed from within the Tangem COS.
     * The issuer defines purpose of use, format and payload of Issuer Data.
     * For example, this field may contain a photo or biometric information for ID card products.
     * Because of the large size of IssuerExtraData, a series of these commands have to be executed
     * to write entire IssuerExtraData.
     *
     * @param cardId: CID, Unique Tangem card ID number.
     * @param issuerData: Data provided by issuer.
     * @param startingSignature: Issuer’s signature with Issuer Data Private Key of [cardId],
     * [issuerDataCounter] (if flags Protect_Issuer_Data_Against_Replay and
     * Restrict_Overwrite_Issuer_Extra_Data are set in [SettingsMask]) and size of [issuerData].
     * @param finalizingSignature: Issuer’s signature with Issuer Data Private Key of [cardId],
     * [issuerData] and [issuerDataCounter] (the latter one only if flags Protect_Issuer_Data_Against_Replay
     * andRestrict_Overwrite_Issuer_Extra_Data are set in [SettingsMask]).
     * @param issuerDataCounter: An optional counter that protect issuer data against replay attack.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used.
     * @param callback: is triggered on the completion of the [WriteIssuerExtraDataCommand] and provides
     * card response in the form of [SuccessResponse] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     */
    @Deprecated(message = "Use files instead")
    fun writeIssuerExtraData(
        cardId: String? = null,
        issuerData: ByteArray,
        startingSignature: ByteArray,
        finalizingSignature: ByteArray,
        issuerDataCounter: Int? = null,
        initialMessage: Message? = null,
        callback: CompletionCallback<SuccessResponse>,
    ) {
        val command = WriteIssuerExtraDataCommand(
            issuerData = issuerData,
            startingSignature = startingSignature,
            finalizingSignature = finalizingSignature,
            issuerDataCounter = issuerDataCounter,
            issuerPublicKey = config.issuerPublicKey,
        )
        startSessionWithRunnable(
            runnable = command,
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * This method launches a [ReadUserDataCommand] on a new thread.
     *
     * This command returns two up to 512-byte User_Data, User_Protected_Data and two counters User_Counter and
     * User_Protected_Counter fields.
     * User_Data and User_ProtectedData are never changed or parsed by the executable code the Tangem COS.
     * The App defines purpose of use, format and it's payload. For example, this field may contain cashed information
     * from blockchain to accelerate preparing new transaction.
     * User_Counter and User_ProtectedCounter are counters, that initial values can be set by App and increased on every signing
     * of new transaction (on SIGN command that calculate new signatures). The App defines purpose of use.
     * For example, this fields may contain blockchain nonce value.
     *
     * @param cardId: CID, Unique Tangem card ID number.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used.
     * @param callback: is triggered on the completion of the [ReadUserDataCommand] and provides
     * card response in the form of [ReadUserDataResponse] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     */
    @Deprecated(message = "Use files instead")
    fun readUserData(
        cardId: String? = null,
        initialMessage: Message? = null,
        callback: CompletionCallback<ReadUserDataResponse>,
    ) {
        val command = ReadUserDataCommand()
        startSessionWithRunnable(
            runnable = command,
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * This method launches a [WriteUserDataCommand] on a new thread, writing  UserData and UserCounter fields.
     *
     * User_Data is never changed or parsed by the executable code the Tangem COS.
     * The App defines purpose of use, format and its payload. For example, this field may contain cashed information
     * from blockchain to accelerate preparing new transaction.
     * The initial value of User_Counter can be set by an App and increased on every signing
     * of new transaction (on SIGN command that calculate new signatures). The App defines purpose of use.
     * For example, this fields may contain blockchain nonce value.
     * Writing of UserCounter and UserData is protected only by Access code.
     *
     * @param cardId: CID, Unique Tangem card ID number.
     * @param userData: A data for which an SDK's user can define its purpose of use,
     * format and it's payload. For example, this field may contain cashed information from blockchain
     * to accelerate preparing new transaction.
     * @param userCounter: A counter that initial value can be set by an SDK's user and
     * increased on every signing of new transaction (on [SignCommand] that calculate new signatures).
     * An SDK's user defines purpose of its use. If null, the current counter value will not be overwritten.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used.
     * @param callback: is triggered on the completion of the [WriteUserDataCommand] and provides
     * card response in the form of [SuccessResponse] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     */
    @Deprecated(message = "Use files instead")
    fun writeUserData(
        userData: ByteArray,
        userCounter: Int? = null,
        cardId: String? = null,
        initialMessage: Message? = null,
        callback: CompletionCallback<SuccessResponse>,
    ) {
        val command = WriteUserDataCommand(userData = userData, userCounter = userCounter)
        startSessionWithRunnable(
            runnable = command,
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    /**
     * This method launches a [WriteUserDataCommand] on a new thread,
     * writing UserProtectedData and UserProtectedCounter fields.
     *
     * User_ProtectedData is never changed or parsed by the executable code the Tangem COS.
     * The App defines purpose of use, format and its payload. For example, this field may contain cashed information
     * from blockchain to accelerate preparing new transaction.
     * The initial value of User_ProtectedCounter can be set by an App and increased on every signing
     * of a new transaction (on SIGN command that calculate new signatures). The App defines the purpose of use.
     * For example, this fields may contain blockchain nonce value.
     * UserProtectedCounter and UserProtectedData require Passcode for confirmation.
     *
     * @param cardId: CID, Unique Tangem card ID number.
     * @param userProtectedData: A data for which an SDK's user can define its purpose of use,
     * format and it's payload. For example, this field may contain cashed information from blockchain
     * to accelerate preparing new transaction.
     * @param userProtectedCounter: A counter that initial value can be set by an SDK's user and
     * increased on every signing of new transaction (on [SignCommand] that calculate new signatures).
     * An SDK's user defines purpose of its use. If null, the current counter value will not be overwritten.
     * For example, this fields may contain blockchain nonce value.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used.
     * @param callback: is triggered on the completion of the [WriteUserDataCommand] and provides
     * card response in the form of [SuccessResponse] if the task was performed successfully
     * or [TangemSdkError] in case of an error.
     */
    @Deprecated(message = "Use files instead")
    fun writeUserProtectedData(
        userProtectedData: ByteArray,
        userProtectedCounter: Int? = null,
        cardId: String? = null,
        initialMessage: Message? = null,
        callback: CompletionCallback<SuccessResponse>,
    ) {
        val command = WriteUserDataCommand(
            userProtectedData = userProtectedData,
            userProtectedCounter = userProtectedCounter,
        )
        startSessionWithRunnable(
            runnable = command,
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = null,
            callback = callback,
        )
    }

    // endregion Card operations

    // region Session start
    /**
     * Allows running a custom bunch of commands in one [CardSession] by creating a custom task.
     * [TangemSdk] will start a card session, perform preflight [ReadCommand],
     * invoke [CardSessionRunnable.run] and close the session.
     * You can find the current card in the [CardSession.environment].
     *
     * @param runnable: A custom task, adopting [CardSessionRunnable] protocol
     * @param cardId: CID, Unique Tangem card ID number. If not null, the SDK will check that you the card
     * with which you tapped a phone has this [cardId] and SDK will return
     * the [TangemSdkError.WrongCardNumber] otherwise.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used.
     * @param accessCode: Access code that will be used for a card session initialization. If null, Tangem SDK will
     * handle it automatically.
     * @param preflightReadFilter preset [PreflightReadFilter] will be used instead of internals
     * @param callback: Standard [TangemSdk] callback.
     */
    fun <T> startSessionWithRunnable(
        runnable: CardSessionRunnable<T>,
        cardId: String? = null,
        initialMessage: Message? = null,
        accessCode: String? = null,
        iconScanRes: Int? = null,
        preflightReadFilter: PreflightReadFilter? = null,
        callback: CompletionCallback<T>,
    ) {
        if (!nfcAvailabilityProvider.isNfcFeatureAvailable()) {
            callback(CompletionResult.Failure(TangemSdkError.NfcFeatureIsUnavailable()))
            return
        }
        if (checkSession()) {
            callback(CompletionResult.Failure(TangemSdkError.Busy()))
            return
        }

        configure()
        cardSession = makeSession(
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = accessCode,
            preflightReadFilter = preflightReadFilter,
        )
        cardSession?.startWithRunnable(
            iconScanRes = iconScanRes,
            runnable = runnable,
            callback = callback,
        )
    }

    /**
     * Allows running  a custom bunch of commands in one [CardSession] with lightweight closure syntax.
     * Tangem SDK will start a card session and perform preflight [ReadCommand].
     *
     * @param cardId: CID, Unique Tangem card ID number. If not null, the SDK will check that you the card
     * with which you tapped a phone has this [cardId] and SDK will return
     * the [TangemSdkError.WrongCardNumber] otherwise.
     * @param initialMessage: A custom description that shows at the beginning of the NFC session.
     * If null, default message will be used.
     * @param accessCode: Access code that will be used for a card session initialization. If null, Tangem SDK will
     * handle it automatically.
     * @param callback: At first, you should check that the [TangemSdkError] is not null,
     * then you can use the [CardSession] to interact with a card.
     */
    fun startSession(
        cardId: String? = null,
        initialMessage: Message? = null,
        accessCode: String? = null,
        callback: (session: CardSession, error: TangemError?) -> Unit,
    ) {
        if (!nfcAvailabilityProvider.isNfcFeatureAvailable()) {
            callback(cardSession!!, TangemSdkError.NfcFeatureIsUnavailable())
            return
        }
        if (checkSession()) {
            callback(cardSession!!, TangemSdkError.Busy())
            return
        }

        configure()
        cardSession = makeSession(
            cardId = cardId,
            initialMessage = initialMessage,
            accessCode = accessCode,
            preflightReadFilter = CardIdPreflightReadFilter.initOrNull(cardId),
        )

        cardSession?.start(onSessionStarted = callback)
    }

    /**
     * Allows running a custom bunch of commands in one NFC Session by creating a custom task. Tangem SDK will start
     * a card session, perform preflight [ReadCommand], invoke the `run ` method of [CardSessionRunnable] and close
     * the session. You can find the current card in the `environment` property of the [CardSession]
     *
     * @param jsonRequest: a `JSONRPCRequest`, describing specific [CardSessionRunnable]
     * @param completion: a `JSONRPCResponse` with result of the operation
     */

    fun startSessionWithJsonRequest(
        jsonRequest: String,
        cardId: String? = null,
        initialMessage: String? = null,
        accessCode: String? = null,
        callback: (String) -> Unit,
    ) {
        if (!nfcAvailabilityProvider.isNfcFeatureAvailable()) {
            throw TangemSdkError.NfcFeatureIsUnavailable()
        }
        val converter = MoshiJsonConverter.INSTANCE
        val linkersList: List<JSONRPCLinker> = try {
            JSONRPCLinker.parse(jsonRequest, converter)
        } catch (ex: JSONRPCException) {
            callback(JSONRPCResponse(null, ex.jsonRpcError, id = null).toJson())
            return
        }

        linkersList.forEach { it.initRunnable(jsonRpcConverter) }
        if (linkersList.any { it.hasError() }) {
            callback(linkersList.createResult(converter))
            return
        }

        try {
            if (checkSession()) throw TangemSdkError.Busy()

            configure()
            val message: Message? = initialMessage?.let { converter.fromJson(it) }

            if (linkersList.size == 1) {
                val jsonrpcLinker = linkersList[0]
                cardSession = makeSession(cardId, message, accessCode)
                Thread().run {
                    cardSession?.startWithRunnable(runnable = jsonrpcLinker.runnable!!) {
                        jsonrpcLinker.linkResult(it)
                        callback(jsonrpcLinker.response.toJson())
                    }
                }
            } else {
                val task = RunnablesTask(linkersList)
                cardSession = makeSession(cardId, message, accessCode)
                cardSession!!.startWithRunnable(runnable = task) { result ->
                    when (result) {
                        is CompletionResult.Success -> callback(converter.toJson(result.data.responses))
                        is CompletionResult.Failure -> {
                            linkersList.forEach { it.linkError(result.error) }
                            callback(linkersList.createResult(converter))
                        }
                    }
                }
            }
        } catch (ex: TangemSdkError) {
            linkersList.forEach { it.linkError(ex) }
            callback(linkersList.createResult(converter))
        }
    }
    // endregion Session start

    /**
     * Register custom task, that supported JSONRPC
     *
     * @param handler, that conforms [JSONRPCHandler]
     */
    fun registerJSONRPCTask(handler: JSONRPCHandler<*>) {
        jsonRpcConverter.register(handler)
    }

    fun setScanImage(scanTagImage: ScanTagImage) {
        config.scanTagImage = scanTagImage
    }

    private fun configure() {
        viewDelegate.setConfig(config)
    }

    private fun createUserCodeRepository(
        authenticationManager: AuthenticationManager,
        keystoreManager: KeystoreManager,
        secureStorage: SecureStorage,
        config: Config,
    ): UserCodeRepository? = runCatching {
        if (authenticationManager.canAuthenticate &&
            config.userCodeRequestPolicy is UserCodeRequestPolicy.AlwaysWithBiometrics
        ) {
            UserCodeRepository(
                keystoreManager = keystoreManager,
                secureStorage = secureStorage,
            )
        } else {
            null
        }
    }.getOrNull()

    private fun makeSession(
        cardId: String? = null,
        initialMessage: Message? = null,
        accessCode: String? = null,
        preflightReadFilter: PreflightReadFilter? = null,
        config: Config = this.config,
    ): CardSession {
        val environment = SessionEnvironment(config, secureStorage)

        if (accessCode != null) {
            environment.accessCode = UserCode(UserCodeType.AccessCode, accessCode)
        }

        return CardSession(
            cardId = cardId,
            viewDelegate = viewDelegate,
            environment = environment,
            userCodeRepository = createUserCodeRepository(
                authenticationManager = authenticationManager,
                keystoreManager = keystoreManager,
                secureStorage = secureStorage,
                config = config,
            ),
            reader = reader,
            jsonRpcConverter = jsonRpcConverter,
            secureStorage = secureStorage,
            initialMessage = initialMessage,
            preflightReadFilter = preflightReadFilter,
        )
    }

    private fun checkSession(): Boolean {
        val session = cardSession ?: return false
        return session.state == CardSession.CardSessionState.Active
    }

    companion object
}

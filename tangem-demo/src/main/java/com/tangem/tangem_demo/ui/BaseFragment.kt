package com.tangem.tangem_demo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.tangem.TangemSdk
import com.tangem.commands.PinType
import com.tangem.commands.SetPinCommand
import com.tangem.commands.WriteIssuerExtraDataCommand
import com.tangem.commands.common.card.Card
import com.tangem.commands.common.card.EllipticCurve
import com.tangem.commands.common.card.masks.SigningMethod
import com.tangem.commands.file.FileData
import com.tangem.commands.file.FileDataSignature
import com.tangem.commands.file.FileSettingsChange
import com.tangem.commands.wallet.WalletConfig
import com.tangem.commands.wallet.WalletIndex
import com.tangem.common.CompletionResult
import com.tangem.common.extensions.calculateSha256
import com.tangem.common.extensions.guard
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.extensions.toByteArray
import com.tangem.common.files.FileHashHelper
import com.tangem.crypto.CryptoUtils
import com.tangem.crypto.sign
import com.tangem.tangem_demo.R
import com.tangem.tangem_demo.Utils
import com.tangem.tangem_demo.postUi

/**
[REDACTED_AUTHOR]
 */
abstract class BaseFragment : Fragment() {

    protected lateinit var sdk: TangemSdk

    protected var card: Card? = null

    protected var intWalletIndex = 0
    protected var walletIndex: WalletIndex = WalletIndex.Index(intWalletIndex)
        get() = WalletIndex.Index(intWalletIndex)
    protected var walletPublicKey: ByteArray? = null
        private set
        get() = card?.wallet(walletIndex)?.publicKey

    protected var bshDlg: BottomSheetDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sdk = initSdk()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(getLayoutId(), container, false)
    }

    protected fun scanCard() {
        sdk.scanCard(walletIndex) { handleResult(it) }
    }

    protected fun sign(hashes: Array<ByteArray>) {
        val publicKey = walletPublicKey.guard {
            showToast("Scan a card before trying to use the sign method")
            return
        }
        sdk.sign(hashes, publicKey, card?.cardId) { handleResult(it) }
    }

    protected fun createWallet() {
        sdk.createWallet(prepareWalletConfig(), card?.cardId) { handleResult(it) }
    }

    protected fun purgeWallet() {
        val index = walletIndex.guard {
            showToast("Select a wallet")
            return
        }
        sdk.purgeWallet(index, card?.cardId) { handleResult(it) }
    }

    protected fun readIssuerData() {
        sdk.readIssuerData(card?.cardId) { handleResult(it) }
    }

    protected fun writeIssuerData() {
        val cardId = card?.cardId
        if (cardId == null) {
            showToast("CardId required. Scan your card before proceeding")
            return
        }

        val issuerData = Utils.randomString(Utils.randomInt(15, 30)).toByteArray()
        val counter = 1
        val issuerPrivateKey = Utils.issuer().dataKeyPair.privateKey
        val signedIssuerData = (cardId.hexToBytes() + issuerData + counter.toByteArray(4)).sign(issuerPrivateKey)

        sdk.writeIssuerData(cardId, issuerData, signedIssuerData, counter) { handleResult(it) }
    }

    @Deprecated("Deprecated in the TangemSdk")
    protected fun readIssuerExtraData() {
        sdk.readIssuerExtraData(card?.cardId) { handleResult(it) }
    }

    @Deprecated("Deprecated in the TangemSdk")
    protected fun writeIssuerExtraData() {
        val cardId = card?.cardId
        if (cardId == null) {
            showToast("CardId required. Scan your card before proceeding")
            return
        }

        val counter = 1
        val issuerData = CryptoUtils.generateRandomBytes(WriteIssuerExtraDataCommand.SINGLE_WRITE_SIZE * 5)
        val signatures = FileHashHelper.prepareHashes(
            cardId, issuerData, counter, Utils.issuer().dataKeyPair.privateKey
        )

        sdk.writeIssuerExtraData(
            cardId, issuerData,
            signatures.startingSignature!!, signatures.finalizingSignature!!,
            counter
        ) { handleResult(it) }
    }

    protected fun verify() {
        sdk.verify(card?.cardId) { handleResult(it) }
    }

    protected fun readUserData() {
        sdk.readUserData(card?.cardId) { handleResult(it) }
    }

    protected fun writeUserData() {
        val userData = "Some of user data ${Utils.randomString(20)}".toByteArray()
        val counter = 1
        sdk.writeUserData(card?.cardId, userData, counter) { handleResult(it) }
    }

    protected fun writeUserProtectedData() {
        val userProtectedData = "Some of user protected data ${Utils.randomString(20)}".toByteArray()
        val counter = 1
        sdk.writeUserProtectedData(card?.cardId, userProtectedData, counter) { handleResult(it) }
    }

    protected fun setPin1() {
        sdk.startSessionWithRunnable(
            SetPinCommand(PinType.Pin1, null, sdk.config.defaultPin2.calculateSha256()), card?.cardId) { handleResult(it) }
    }

    protected fun setPin2() {
        sdk.startSessionWithRunnable(
            SetPinCommand(PinType.Pin2, sdk.config.defaultPin1.calculateSha256(), null), card?.cardId) { handleResult(it) }
    }

    protected fun readFiles(readPrivateFiles: Boolean) {
        sdk.readFiles(readPrivateFiles) { handleResult(it) }
    }

    protected fun writeFilesSigned() {
        val cardId = card?.cardId
        if (cardId == null) {
            showToast("CardId required. Scan your card before proceeding")
            return
        }
        val file = prepareSignedData(cardId)
        sdk.writeFiles(listOf(file)) { handleResult(it) }
    }

    protected fun writeFilesWithPassCode() {
        val issuerData = CryptoUtils.generateRandomBytes(WriteIssuerExtraDataCommand.SINGLE_WRITE_SIZE * 5)
        val files = listOf(FileData.DataProtectedByPasscode(issuerData), FileData.DataProtectedByPasscode(issuerData))
        sdk.writeFiles(files) { handleResult(it) }
    }

    protected fun deleteFiles(indices: List<Int>? = null) {
        sdk.deleteFiles(indices) { handleResult(it) }
    }

    protected fun changeFilesSettings(change: FileSettingsChange) {
        sdk.changeFilesSettings(listOf(change)) { handleResult(it) }
    }

    private fun handleResult(result: CompletionResult<*>) {
        setCard(result)
        postUi { handleCommandResult(result) }
    }

    private fun setCard(completionResult: CompletionResult<*>) {
        when (completionResult) {
            is CompletionResult.Success -> {
                if (completionResult.data is Card) card = completionResult.data as Card
            }
            is CompletionResult.Failure -> {
            }
        }
    }

    protected fun showDialog(message: String) {
        val dlg = bshDlg ?: BottomSheetDialog(requireActivity())
        if (dlg.isShowing) dlg.hide()

        dlg.setContentView(R.layout.bottom_sheet_response_layout)
        val tv = dlg.findViewById<TextView>(R.id.tvResponse) ?: return

        tv.text = message
        dlg.show()
    }

    protected fun showToast(message: String) {
        Toast.makeText(requireActivity(), message, Toast.LENGTH_LONG).show()
    }

    protected fun prepareHashesToSign(): Array<ByteArray> {
        val listOfData = MutableList(10) { Utils.randomString(20) }
        val listOfHashes = listOfData.map { it.toByteArray() }
        return listOfHashes.toTypedArray()
    }

    protected fun prepareSignedData(cardId: String): FileData.DataProtectedBySignature {
        val counter = 1
        val issuerData = CryptoUtils.generateRandomBytes(WriteIssuerExtraDataCommand.SINGLE_WRITE_SIZE * 5)
        val signatures = FileHashHelper.prepareHashes(
            cardId, issuerData, counter, Utils.issuer().dataKeyPair.privateKey
        )
        return FileData.DataProtectedBySignature(
            issuerData, counter,
            FileDataSignature(signatures.startingSignature!!, signatures.finalizingSignature!!),
            Utils.issuer().dataKeyPair.publicKey
        )
    }

    protected fun prepareWalletConfig(): WalletConfig {
        return WalletConfig(
            true,
            false,
            EllipticCurve.Secp256k1,
            SigningMethod.SignHash,
        )
    }

    protected abstract fun getLayoutId(): Int
    protected abstract fun initSdk(): TangemSdk
    abstract fun handleCommandResult(result: CompletionResult<*>)
}
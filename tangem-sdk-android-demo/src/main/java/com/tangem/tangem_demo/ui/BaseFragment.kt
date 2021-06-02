package com.tangem.tangem_demo.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.tangem.Message
import com.tangem.TangemSdk
import com.tangem.commands.WriteIssuerExtraDataCommand
import com.tangem.commands.common.card.Card
import com.tangem.commands.file.DataToWrite
import com.tangem.commands.file.FileDataSignature
import com.tangem.commands.file.FileSettingsChange
import com.tangem.commands.wallet.CreateWalletResponse
import com.tangem.commands.wallet.PurgeWalletResponse
import com.tangem.commands.wallet.WalletConfig
import com.tangem.commands.wallet.WalletIndex
import com.tangem.common.CompletionResult
import com.tangem.common.extensions.guard
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.extensions.toByteArray
import com.tangem.common.files.FileHashHelper
import com.tangem.crypto.CryptoUtils
import com.tangem.crypto.sign
import com.tangem.tangem_demo.DemoApplication
import com.tangem.tangem_demo.R
import com.tangem.tangem_demo.Utils
import com.tangem.tangem_demo.postUi
import com.tangem.tangem_demo.ui.settings.SettingsFragment

/**
[REDACTED_AUTHOR]
 */
abstract class BaseFragment : Fragment() {

    protected lateinit var shPrefs: SharedPreferences
    protected lateinit var sdk: TangemSdk

    protected var card: Card? = null
    protected var intWalletIndex = -1
    protected var initialMessage: Message? = null
        private set

    protected var selectedWalletPubKey: ByteArray? = null
        private set
        get() {
            return if (intWalletIndex == -1) {
                showToast("Scan the card before trying to use the method")
                null
            } else {
                card?.wallet(WalletIndex.Index(intWalletIndex))?.publicKey.guard {
                    showToast("PublicKey with the selected index: $intWalletIndex not found")
                    return null
                }
            }
        }

    protected var bshDlg: BottomSheetDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shPrefs = (requireContext().applicationContext as DemoApplication).shPrefs
        sdk = initSdk()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(getLayoutId(), container, false)
    }

    override fun onResume() {
        super.onResume()
        initInitialMessage()
    }

    private fun initInitialMessage() {
        if (!shPrefs.getBoolean(SettingsFragment.initialMessageEnabled, false)) {
            initialMessage = null
            return
        }
        val header = shPrefs.getString(SettingsFragment.initialMessageHeader, null)
        val body = shPrefs.getString(SettingsFragment.initialMessageBody, null)
        if (header == null && body == null) return

        initialMessage = Message(header, body)
    }

    protected fun scanCard(updateOnlyCard: Boolean = false) {
        sdk.scanCard(initialMessage) { if (updateOnlyCard) setCard(it) else handleResult(it) }
    }

    protected fun sign(hashes: Array<ByteArray>) {
        val publicKey = selectedWalletPubKey ?: return
        sdk.sign(hashes, publicKey, card?.cardId, initialMessage) { handleResult(it) }
    }

    protected fun createWallet(walletConfig: WalletConfig) {
        sdk.createWallet(walletConfig, card?.cardId, initialMessage) { handleResult(it) }
    }

    protected fun purgeWallet() {
        val publicKey = selectedWalletPubKey ?: return
        sdk.purgeWallet(WalletIndex.PublicKey(publicKey), card?.cardId, initialMessage) { handleResult(it) }
    }

    protected fun readIssuerData() {
        sdk.readIssuerData(card?.cardId, initialMessage) { handleResult(it) }
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

        sdk.writeIssuerData(cardId, issuerData, signedIssuerData, counter, initialMessage) { handleResult(it) }
    }

    @Deprecated("Deprecated in the TangemSdk")
    protected fun readIssuerExtraData() {
        sdk.readIssuerExtraData(card?.cardId, initialMessage) { handleResult(it) }
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
            counter, initialMessage
        ) { handleResult(it) }
    }

    protected fun verify() {
        sdk.verify(false, initialMessage = initialMessage) { handleResult(it) }
    }

    protected fun readUserData() {
        sdk.readUserData(card?.cardId, initialMessage) { handleResult(it) }
    }

    protected fun writeUserData() {
        val userData = "Some of user data ${Utils.randomString(20)}".toByteArray()
        val counter = 1
        sdk.writeUserData(card?.cardId, userData, counter, initialMessage) { handleResult(it) }
    }

    protected fun writeUserProtectedData() {
        val userProtectedData = "Some of user protected data ${Utils.randomString(20)}".toByteArray()
        val counter = 1
        sdk.writeUserProtectedData(card?.cardId, userProtectedData, counter, initialMessage) { handleResult(it) }
    }

    protected fun setPin1() {
        sdk.changePin1(initialMessage = initialMessage) { handleResult(it) }
    }

    protected fun setPin2() {
        sdk.changePin2(initialMessage = initialMessage) { handleResult(it) }
    }

    protected fun readFiles(readPrivateFiles: Boolean) {
        sdk.readFiles(readPrivateFiles, initialMessage = initialMessage) { handleResult(it) }
    }

    protected fun writeFilesSigned() {
        val cardId = card?.cardId
        if (cardId == null) {
            showToast("CardId required. Scan your card before proceeding")
            return
        }
        val file = prepareSignedData(cardId)
        sdk.writeFiles(listOf(file), initialMessage = initialMessage) { handleResult(it) }
    }

    protected fun writeFilesWithPassCode() {
        val issuerData = CryptoUtils.generateRandomBytes(WriteIssuerExtraDataCommand.SINGLE_WRITE_SIZE * 5)
        val files = listOf(DataToWrite.DataProtectedByPasscode(issuerData), DataToWrite.DataProtectedByPasscode(issuerData))
        sdk.writeFiles(files, initialMessage = initialMessage) { handleResult(it) }
    }

    protected fun deleteFiles(indices: List<Int>? = null) {
        sdk.deleteFiles(indices, initialMessage = initialMessage) { handleResult(it) }
    }

    protected fun changeFilesSettings(change: FileSettingsChange) {
        sdk.changeFilesSettings(listOf(change), initialMessage = initialMessage) { handleResult(it) }
    }

    private fun handleResult(result: CompletionResult<*>) {
        setCard(result)
        postUi { handleCommandResult(result) }
    }

    private fun setCard(completionResult: CompletionResult<*>) {
        when (completionResult) {
            is CompletionResult.Success -> {
                when (completionResult.data) {
                    is Card -> card = completionResult.data as Card
                    is CreateWalletResponse, is PurgeWalletResponse -> {
                        scanCard(true)
                    }
                }
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
        activity?.let { Toast.makeText(it, message, Toast.LENGTH_LONG).show() }
    }

    protected fun prepareHashesToSign(): Array<ByteArray> {
        val listOfData = MutableList(10) { Utils.randomString(20) }
        val listOfHashes = listOfData.map { it.toByteArray() }
        return listOfHashes.toTypedArray()
    }

    protected fun prepareSignedData(cardId: String): DataToWrite.DataProtectedBySignature {
        val counter = 1
        val issuerData = CryptoUtils.generateRandomBytes(WriteIssuerExtraDataCommand.SINGLE_WRITE_SIZE * 5)
        val signatures = FileHashHelper.prepareHashes(
            cardId, issuerData, counter, Utils.issuer().dataKeyPair.privateKey
        )
        return DataToWrite.DataProtectedBySignature(
            issuerData, counter,
            FileDataSignature(signatures.startingSignature!!, signatures.finalizingSignature!!),
            Utils.issuer().dataKeyPair.publicKey
        )
    }

    protected abstract fun getLayoutId(): Int
    protected abstract fun initSdk(): TangemSdk
    abstract fun handleCommandResult(result: CompletionResult<*>)
}
package com.tangem.tangem_demo

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import com.tangem.Config
import com.tangem.TangemSdk
import com.tangem.commands.Card
import com.tangem.commands.WriteIssuerExtraDataCommand
import com.tangem.commands.common.ResponseConverter
import com.tangem.common.CompletionResult
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.extensions.toByteArray
import com.tangem.crypto.CryptoUtils
import com.tangem.crypto.sign
import com.tangem.tangem_sdk_new.extensions.init
import kotlinx.android.synthetic.main.issuer_data.*
import kotlinx.android.synthetic.main.issuer_ex_data.*
import kotlinx.android.synthetic.main.scan_card.*
import kotlinx.android.synthetic.main.set_pin.*
import kotlinx.android.synthetic.main.sign.*
import kotlinx.android.synthetic.main.user_data.*
import kotlinx.android.synthetic.main.wallet.*

class DemoActivity : AppCompatActivity() {

    private val gson: Gson = ResponseConverter().gson
    private val mainThread = Handler(Looper.getMainLooper())

    private lateinit var sdk: TangemSdk
    private var card: Card? = null

    private var bshDlg: BottomSheetDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_demo)

        sdk = TangemSdk.init(this, Config())
        scanCard()
        sign()
        wallet()
        issuerData()
        issuerExData()
        userData()
        setPin()
    }

	private fun scanCard() {
		btnScanCard.setOnClickListener { sdk.scanCard { handleResult(it) } }
	}

    private fun sign() {
        btnSign.setOnClickListener {
            val listOfData = MutableList(Utils.randomInt(1, 10)) { Utils.randomString(20) }
            val listOfHashes = listOfData.map { it.toByteArray() }
            sdk.sign(listOfHashes.toTypedArray(), card?.cardId) { handleResult(it) }
        }
    }

    private fun wallet() {
        btnCreateWallet.setOnClickListener { sdk.createWallet(card?.cardId) { handleResult(it) } }
        btnPurgeWallet.setOnClickListener { sdk.purgeWallet(card?.cardId) { handleResult(it) } }
    }

    private fun issuerData() {
        btnReadIssuerData.setOnClickListener { sdk.readIssuerData(card?.cardId) { handleResult(it) } }

        btnWriteIssuerData.setOnClickListener {
            val cardId = card?.cardId
            if (cardId == null) {
                showToast("CardId required. Scan your card before proceeding")
                return@setOnClickListener
            }

            val issuerData = Utils.randomString(Utils.randomInt(15, 30)).toByteArray()
            val counter = 1
            val issuerPrivateKey = Utils.issuer().dataKeyPair.privateKey
            val signedIssuerData = (cardId.hexToBytes() + issuerData + counter.toByteArray(4)).sign(issuerPrivateKey)

            sdk.writeIssuerData(cardId, issuerData, signedIssuerData, counter) { handleResult(it) }
        }
    }

    private fun issuerExData() {
        btnReadIssuerExData.setOnClickListener { sdk.readIssuerExtraData(card?.cardId) { handleResult(it) } }

        btnWriteIssuerExData.setOnClickListener {
            val cardId = card?.cardId
            if (cardId == null) {
                showToast("CardId required. Scan your card before proceeding")
                return@setOnClickListener
            }

            val counter = 1
            val issuerData = CryptoUtils.generateRandomBytes(WriteIssuerExtraDataCommand.SINGLE_WRITE_SIZE * 5)
            val issuerPrivateKey = Utils.issuer().dataKeyPair.privateKey
            val startingSignature = (cardId.hexToBytes() + counter.toByteArray(4) + issuerData.size.toByteArray(2)).sign(issuerPrivateKey)
            val finalizingSignature = (cardId.hexToBytes() + issuerData + counter.toByteArray(4)).sign(issuerPrivateKey)

            sdk.writeIssuerExtraData(cardId, issuerData, startingSignature, finalizingSignature, counter) { handleResult(it) }
        }

    }

    private fun userData() {
        btnReadUserData.setOnClickListener { sdk.readUserData(card?.cardId) { handleResult(it) } }

        btnWriteUserData.setOnClickListener {
            val userData = "Some of user data ${Utils.randomString(20)}".toByteArray()
            val counter = 1
            sdk.writeUserData(card?.cardId, userData, counter) { handleResult(it) }
        }

        btnWriteUserProtectedData.setOnClickListener {
            val userProtectedData = "Some of user protected data ${Utils.randomString(20)}".toByteArray()
            val counter = 1
            sdk.writeUserProtectedData(card?.cardId, userProtectedData, counter) { handleResult(it) }
        }
    }

    private fun setPin() {
//        fun getPin(): ByteArray? {
//            val pin = etPinCode.text.toString()
//            return if (pin.isEmpty()) null else pin.calculateSha256()
//        }
//        btnSetPin1.setOnClickListener { sdk.changePin1(card?.cardId, getPin()) { handleResult(it) } }
//        btnSetPin2.setOnClickListener { sdk.changePin2(card?.cardId, getPin()) { handleResult(it) } }
		btnSetPin1.setOnClickListener { sdk.changePin1(card?.cardId) { handleResult(it) } }
		btnSetPin2.setOnClickListener { sdk.changePin2(card?.cardId) { handleResult(it) } }
    }

    private fun handleResult(completionResult: CompletionResult<*>) {
        when (completionResult) {
            is CompletionResult.Success -> {
                if (completionResult.data is Card) {
                    card = completionResult.data as Card
                }

                val json = gson.toJson(completionResult.data)
                mainThread.post { showDialog(json) }
            }
            is CompletionResult.Failure -> {
                mainThread.post { showToast(completionResult.error.customMessage) }
            }
        }
    }

    private fun showDialog(message: String) {
        val dlg = bshDlg ?: BottomSheetDialog(this)
        if (dlg.isShowing) dlg.hide()

        dlg.setContentView(R.layout.bottom_sheet_response_layout)
        val tv = dlg.findViewById<TextView>(R.id.tvResponse) ?: return

        tv.text = message
        dlg.show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
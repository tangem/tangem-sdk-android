package com.tangem.tangem_demo.ui.separtedCommands

import android.os.Bundle
import android.view.View
import com.google.android.material.slider.Slider
import com.tangem.Config
import com.tangem.Log
import com.tangem.TangemSdk
import com.tangem.TangemSdkError
import com.tangem.commands.common.card.Card
import com.tangem.commands.common.card.EllipticCurve
import com.tangem.commands.common.card.masks.SigningMethod
import com.tangem.commands.common.jsonConverter.MoshiJsonConverter
import com.tangem.commands.file.FileSettings
import com.tangem.commands.file.FileSettingsChange
import com.tangem.commands.wallet.WalletConfig
import com.tangem.common.CompletionResult
import com.tangem.tangem_demo.DemoActivity
import com.tangem.tangem_demo.R
import com.tangem.tangem_demo.ui.BaseFragment
import com.tangem.tangem_sdk_new.DefaultSessionViewDelegate
import com.tangem.tangem_sdk_new.extensions.init
import kotlinx.android.synthetic.main.file_data.*
import kotlinx.android.synthetic.main.issuer_data.*
import kotlinx.android.synthetic.main.issuer_ex_data.*
import kotlinx.android.synthetic.main.scan_card.*
import kotlinx.android.synthetic.main.set_pin.*
import kotlinx.android.synthetic.main.sign.*
import kotlinx.android.synthetic.main.user_data.*
import kotlinx.android.synthetic.main.wallet.*

/**
[REDACTED_AUTHOR]
 */
class CommandListFragment : BaseFragment() {

    private val jsonConverter: MoshiJsonConverter = MoshiJsonConverter.default()
    private val logger = DefaultSessionViewDelegate.createLogger()

    override fun initSdk(): TangemSdk = TangemSdk.init(this.requireActivity(), Config())

    override fun getLayoutId(): Int = R.layout.fg_command_list

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? DemoActivity)?.listenPageChanges {
            if (it == 0) Log.addLogger(logger)
            else Log.removeLogger(logger)
        }
        btnScanCard.setOnClickListener { scanCard() }
        btnSign.setOnClickListener { sign(prepareHashesToSign()) }

        btnCreateWalletSecpK1.setOnClickListener { createWallet(createWalletConfig(EllipticCurve.Secp256k1)) }
        btnCreateWalletSecpR1.setOnClickListener { createWallet(createWalletConfig(EllipticCurve.Secp256r1)) }
        btnCreateWalletEdwards.setOnClickListener { createWallet(createWalletConfig(EllipticCurve.Ed25519)) }
        btnPurgeWallet.setOnClickListener { purgeWallet() }

        btnReadIssuerData.setOnClickListener { readIssuerData() }
        btnWriteIssuerData.setOnClickListener { writeIssuerData() }

        btnReadIssuerExData.setOnClickListener { readIssuerExtraData() }
        btnWriteIssuerExData.setOnClickListener { writeIssuerExtraData() }

        btnReadUserData.setOnClickListener { readUserData() }
        btnWriteUserData.setOnClickListener { writeUserData() }
        btnWriteUserProtectedData.setOnClickListener { writeUserProtectedData() }

        btnSetPin1.setOnClickListener { setPin1() }
        btnSetPin2.setOnClickListener { setPin2() }

        btnReadAllFiles.setOnClickListener { readFiles(true) }
        btnReadPublicFiles.setOnClickListener { readFiles(false) }
        btnWriteSignedFile.setOnClickListener { writeFilesSigned() }
        btnWriteFilesWithPasscode.setOnClickListener { writeFilesWithPassCode() }
        btnDeleteAll.setOnClickListener { deleteFiles() }
        btnDeleteFirst.setOnClickListener { deleteFiles(listOf(0)) }
        btnMakeFilePublic.setOnClickListener { changeFilesSettings(FileSettingsChange(0, FileSettings.Public)) }
        btnMakeFilePrivate.setOnClickListener { changeFilesSettings(FileSettingsChange(0, FileSettings.Private)) }
    }

    private fun createWalletConfig(curve: EllipticCurve): WalletConfig {
        return WalletConfig(swIsReusable.isChecked, swIsProhibitPurgeWallet.isChecked, curve, SigningMethod.SignHash)
    }

    override fun handleCommandResult(result: CompletionResult<*>) {
        when (result) {
            is CompletionResult.Success -> {
                if (result.data is Card) updateWalletsSlider()
                val json = jsonConverter.prettyPrint(result.data)
                showDialog(json)
            }
            is CompletionResult.Failure -> {
                if (result.error is TangemSdkError.UserCancelled) {
                    showToast("${result.error.customMessage}: User was cancelled the operation")
                } else {
                    showToast(result.error.customMessage)
                }
            }
        }
    }

    private fun updateWalletsSlider() {
        sliderWallet.post {
            sliderWallet.removeOnSliderTouchListener(touchListener)
            if (card?.walletsCount == null) {
                walletIndexesContainer.visibility = View.GONE
                sliderWallet.removeOnSliderTouchListener(touchListener)
                intWalletIndex = 0
            } else {
                val walletsCount = card?.walletsCount?.toFloat() ?: 1f
                if (walletsCount > 1) {
                    walletIndexesContainer.visibility = View.VISIBLE
                    sliderWallet.stepSize = 1f
                    sliderWallet.valueFrom = 0f
                    sliderWallet.valueTo = walletsCount - 1f

                    if (intWalletIndex == -1) intWalletIndex = 0
                    sliderWallet.value = intWalletIndex.toFloat()
                    tvWalletIndex.text = "$intWalletIndex"
                    sliderWallet.addOnSliderTouchListener(touchListener)
                }

            }
        }
    }

    private val touchListener = object : Slider.OnSliderTouchListener {
        override fun onStartTrackingTouch(slider: Slider) {
        }

        override fun onStopTrackingTouch(slider: Slider) {
            intWalletIndex = slider.value.toInt()
            tvWalletIndex.text = "$intWalletIndex"
        }
    }
}
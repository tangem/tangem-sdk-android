package com.tangem.demo.ui.separtedCommands

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import com.google.android.material.slider.Slider
import com.tangem.common.CompletionResult
import com.tangem.common.UserCodeType
import com.tangem.common.card.Card
import com.tangem.common.card.EllipticCurve
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.Config
import com.tangem.common.core.TangemSdkError
import com.tangem.common.core.UserCodeRequestPolicy
import com.tangem.common.extensions.toHexString
import com.tangem.demo.Backup
import com.tangem.demo.postUi
import com.tangem.demo.ui.BaseFragment
import com.tangem.demo.ui.backup.BackupActivity
import com.tangem.demo.ui.extension.beginDelayedTransition
import com.tangem.demo.ui.extension.copyToClipboard
import com.tangem.demo.ui.extension.fitChipsByGroupWidth
import com.tangem.demo.ui.extension.setTextFromClipboard
import com.tangem.demo.ui.separtedCommands.task.MultiMessageTask
import com.tangem.demo.ui.separtedCommands.task.ResetToFactorySettingsTask
import com.tangem.operations.GetEntropyCommand
import com.tangem.operations.GetEntropyMode
import com.tangem.operations.attestation.AttestationTask
import com.tangem.operations.files.FileVisibility
import com.tangem.operations.usersetttings.SetUserSettingsTask
import com.tangem.tangem_demo.R
import com.tangem.tangem_demo.databinding.FgCommandListBinding

/**
[REDACTED_AUTHOR]
 */
class CommandListFragment : BaseFragment() {

    private var jsonRpcSingleCommandTemplate: String = """
    {
        "jsonrpc": "2.0",
        "id": 2,
        "method": "scan",
        "params": {}
    }
    """.trim()

    private var jsonRpcListCommandsTemplate: String = """
    [
        {
          "method": "scan",
          "params": {},
          "id": 1,
          "jsonrpc": "2.0"
        },
        {
          "method": "create_wallet",
          "params": {
            "curve": "Secp256k1"
          },
          "jsonrpc": "2.0"
        },
        {
          "method": "scan",
          "id": 2,
          "jsonrpc": "2.0"
        }
    ]
    """.trim()

    private var _binding: FgCommandListBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val sliderTouchListener = object : Slider.OnSliderTouchListener {
        @SuppressLint("RestrictedApi")
        override fun onStartTrackingTouch(slider: Slider) {
        }

        @SuppressLint("RestrictedApi")
        override fun onStopTrackingTouch(slider: Slider) {
            selectedIndexOfWallet = slider.value.toInt()
            updateWalletInfo()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FgCommandListBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardView.chipGroupUserCodeRequestPolicy.fitChipsByGroupWidth()
        binding.cardView.chipGroupUserCodeRequestPolicy.setOnCheckedChangeListener { _, checkedId ->
            val showTypeSelector = checkedId == R.id.chipPolicyAlways ||
                checkedId == R.id.chipPolicyAlwaysWithBiometrics
            binding.cardView.chipGroupUserCodeType.isVisible = showTypeSelector
            binding.cardView.userCodeRequestPolicyDivider.isVisible = showTypeSelector
        }
        binding.cardView.btnScanCard.setOnClickListener {
            val type = when (binding.cardView.chipGroupUserCodeType.checkedChipId) {
                R.id.chipTypeAccessCode -> UserCodeType.AccessCode
                R.id.chipTypePasscode -> UserCodeType.Passcode
                else -> UserCodeType.AccessCode
            }
            val policy = when (binding.cardView.chipGroupUserCodeRequestPolicy.checkedChipId) {
                R.id.chipPolicyDefault -> UserCodeRequestPolicy.Default
                R.id.chipPolicyAlways -> UserCodeRequestPolicy.Always(type)
                R.id.chipPolicyAlwaysWithBiometrics -> UserCodeRequestPolicy.AlwaysWithBiometrics(type)
                else -> Config().userCodeRequestPolicy
            }
            scanCard(policy)
        }
        binding.cardView.btnLoadCardInfo.setOnClickListener { loadCardInfo() }

        binding.backupView.btnPersonalizePrimary.setOnClickListener { personalize(Backup.primaryCardConfig()) }
        binding.backupView.btnPersonalizeBackup1.setOnClickListener { personalize(Backup.backup1Config()) }
        binding.backupView.btnPersonalizeBackup2.setOnClickListener { personalize(Backup.backup2Config()) }
        binding.backupView.btnDepersonalize.setOnClickListener { depersonalize() }
        binding.backupView.btnPersonalizePrimaryV7.setOnClickListener { personalize(Backup.primaryCardConfigV7()) }
        binding.backupView.btnPersonalizeBackup1V7.setOnClickListener { personalize(Backup.backup1ConfigV7()) }
        binding.backupView.btnPersonalizeBackup2V7.setOnClickListener { personalize(Backup.backup2ConfigV7()) }
        binding.backupView.btnDepersonalize.setOnClickListener { depersonalize() }

        binding.backupView.btnStartBackup.setOnClickListener {
            val intent = Intent(requireContext(), BackupActivity::class.java)
            startActivity(intent)
        }

        binding.attestationView.chipGroupAttest.fitChipsByGroupWidth()
        binding.attestationView.btnAttest.setOnClickListener {
            val mode = when (binding.attestationView.chipGroupAttest.checkedChipId) {
                R.id.chipAttestOffline -> AttestationTask.Mode.Offline
                R.id.chipAttestNormal -> AttestationTask.Mode.Normal
                else -> AttestationTask.Mode.Full
            }
            attest(mode)
        }
        binding.attestationView.btnAttestCardKey.setOnClickListener { attestCardKey() }

        val adapter = ArrayAdapter(
            view.context,
            android.R.layout.simple_dropdown_item_1line,
            listOf(
                "m/0/1",
                "m/0'/1'/2",
                "m/44'/0'/0'/1/0",
            ),
        )

        binding.hdWalletView.etDerivePublicKey.setAdapter(adapter)
        binding.hdWalletView.etDerivePublicKey.addTextChangedListener {
            derivationPath =
                if (it!!.isEmpty()) null else it.toString()
        }
        binding.hdWalletView.btnDerivePublicKey.setOnClickListener { derivePublicKey() }

        binding.signView.btnPasteHashes.setOnClickListener { binding.signView.etHashesToSign.setTextFromClipboard() }
        binding.signView.btnSignHash.setOnClickListener { sign(SignStrategyType.SINGLE) }
        binding.signView.btnSignHashes.setOnClickListener { sign(SignStrategyType.MULTIPLE) }
        binding.deterministicEntropy.etDeterministicEntropyPath.setAdapter(
            ArrayAdapter(
                view.context,
                android.R.layout.simple_dropdown_item_1line,
                listOf(
                    "bip39: m/83696968'/39'/{language}'/{words}'/{index}'",
                    "xprv: m/83696968'/32'/{index}",
                    "hex: m/83696968'/128169'/{num_bytes}'/{index}'",
                    "PWD BASE 64: m/83696968'/707764'/{pwd_len}'/{index}'"
                ),
            )
        )
        binding.deterministicEntropy.btnDeriveEntropy.setOnClickListener {
            deriveEntropy(
                binding.deterministicEntropy.etDeterministicEntropyPath.text.toString()
            )
        }

        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            EllipticCurve.values(),
        )
        binding.walletView.spinnerCurves.adapter = spinnerAdapter
        binding.walletView.btnCreateWallet.setOnClickListener {
            createOrImportWallet(
                binding.walletView.spinnerCurves.selectedItem as EllipticCurve,
                binding.walletView.etMnemonic.text?.toString(),
            )
        }
        binding.walletView.btnPasteMnemonic.setOnClickListener { binding.walletView.etMnemonic.setTextFromClipboard() }

        binding.walletView.btnPurgeWallet.setOnClickListener { purgeWallet() }
        binding.walletView.btnPurgeAllWallet.setOnClickListener { purgeAllWallet() }

        binding.depricated.issuerDataView.btnReadIssuerData.setOnClickListener { readIssuerData() }
        binding.depricated.issuerDataView.btnWriteIssuerData.setOnClickListener { writeIssuerData() }
        binding.masterSecret.btnCreateMasterSecret.setOnClickListener {
            createOrImportMasterSecret(
                binding.masterSecret.etMasterSecretMnemonic.text?.toString(),
            )
        }
        binding.masterSecret.btnPasteMasterSecretMnemonic.setOnClickListener {
            binding.masterSecret.etMasterSecretMnemonic
                .setTextFromClipboard()
        }

        binding.depricated.issuerExDataView.btnReadIssuerExData.setOnClickListener { readIssuerExtraData() }
        binding.depricated.issuerExDataView.btnWriteIssuerExData.setOnClickListener { writeIssuerExtraData() }

        binding.depricated.userDataView.btnReadUserData.setOnClickListener { readUserData() }
        binding.depricated.userDataView.btnWriteUserData.setOnClickListener { writeUserData() }
        binding.depricated.userDataView.btnWriteUserProtectedData.setOnClickListener { writeUserProtectedData() }

        binding.setPinView.btnSetAccessCode.setOnClickListener { setAccessCode() }
        binding.setPinView.btnSetPasscode.setOnClickListener { setPasscode() }
        binding.setPinView.btnResetUserCodes.setOnClickListener { resetUserCodes() }
        binding.setPinView.btnClearUserCodes.setOnClickListener {
            clearUserCodes()
            binding.setPinView.userCodeRepositoryContainer.isVisible = hasSavedUserCodes()
        }
        binding.setPinView.btnDeleteUserCode.setOnClickListener {
            deleteUserCodeForScannedCard()
            if (hasSavedUserCodes()) {
                binding.setPinView.btnDeleteUserCode.isVisible = hasSavedUserCodeForScannedCard()
            } else {
                binding.setPinView.userCodeRepositoryContainer.isVisible = false
            }
        }

        binding.fileDataView.btnReadAllFiles.setOnClickListener { readFiles(true) }
        binding.fileDataView.btnReadPublicFiles.setOnClickListener { readFiles(false) }
        binding.fileDataView.btnWriteUserFile.setOnClickListener { writeUserFile() }
        binding.fileDataView.btnWriteOwnerFile.setOnClickListener { writeOwnerFile() }
        binding.fileDataView.btnDeleteAll.setOnClickListener { deleteFiles() }
        binding.fileDataView.btnDeleteFirst.setOnClickListener { deleteFiles(listOf(0)) }
        binding.fileDataView.btnMakeFilePublic.setOnClickListener {
            changeFilesSettings(
                mapOf(0 to FileVisibility.Public),
            )
        }
        binding.fileDataView.btnMakeFilePrivate.setOnClickListener {
            changeFilesSettings(
                mapOf(0 to FileVisibility.Private),
            )
        }
        binding.setPinView.btnClearAccessTokens.setOnClickListener {
            clearCardTokens()
            binding.setPinView.cardTokensRepositoryContainer.isVisible = hasSavedCardTokens()
        }
        binding.setPinView.btnDeleteUserCode.setOnClickListener {
            deleteCardTokensForScannedCard()
            if (hasSavedCardTokens()) {
                binding.setPinView.btnDeleteAccessToken.isVisible = hasSavedCardTokensForScannedCard()
            } else {
                binding.setPinView.cardTokensRepositoryContainer.isVisible = false
            }
        }

        binding.jRpcView.etJsonRpc.setText(jsonRpcSingleCommandTemplate)
        binding.jRpcView.btnSingleJsonRpc.setOnClickListener {
            binding.jRpcView.etJsonRpc.setText(
                jsonRpcSingleCommandTemplate,
            )
        }
        binding.jRpcView.btnListJsonRpc.setOnClickListener {
            binding.jRpcView.etJsonRpc.setText(
                jsonRpcListCommandsTemplate,
            )
        }
        binding.jRpcView.btnPasteJsonRpc.setOnClickListener { binding.jRpcView.etJsonRpc.setTextFromClipboard() }
        binding.jRpcView.btnLaunchJsonRpc.setOnClickListener {
            launchJSONRPC(
                binding.jRpcView.etJsonRpc.text.toString()
                    .trim(),
            )
        }

        binding.utilsView.btnResetToFactory.setOnClickListener {
            sdk.startSessionWithRunnable(ResetToFactorySettingsTask()) {
                postUi { handleCommandResult(it) }
            }
        }
        binding.utilsView.btnGetEntropy.setOnClickListener {
            sdk.startSessionWithRunnable(GetEntropyCommand(mode = GetEntropyMode.Random)) {
                postUi { handleCommandResult(it) }
            }
        }
        binding.userSettings.chipGroupUserCodeRecoveryAllowed.fitChipsByGroupWidth()
        binding.userSettings.chipGroupPinRequired.fitChipsByGroupWidth()
        binding.userSettings.chipGroupNdef.fitChipsByGroupWidth()
        binding.userSettings.btnSetUserSettings.setOnClickListener {
            val isUserCodeRecoveryAllowed = when (binding.userSettings.chipGroupUserCodeRecoveryAllowed.checkedChipId) {
                R.id.chipUserCodeRecoveryEnable -> true
                R.id.chipUserCodeRecoveryDisable -> false
                else -> false
            }
            val isPinRequired = if( card==null || card!!.firmwareVersion>=FirmwareVersion.v7 ) {
                when (binding.userSettings.chipGroupPinRequired.checkedChipId) {
                    R.id.chipPinRequiredEnable -> true
                    R.id.chipPinRequiredDisable -> false
                    else -> false
                }
            }else null
            val isNdefDisabled =if( card==null || card!!.firmwareVersion>=FirmwareVersion.v7 ) {
                when (binding.userSettings.chipGroupNdef.checkedChipId) {
                    R.id.chipNdefDisable -> true
                    R.id.chipNdefEnable -> false
                    else -> false
                }
            }else null
            sdk.startSessionWithRunnable(SetUserSettingsTask(isUserCodeRecoveryAllowed,isPinRequired,isNdefDisabled)) {
                postUi { handleCommandResult(it) }
            }
        }
        binding.utilsView.btnCheckSetMessage.setOnClickListener {
            sdk.startSessionWithRunnable(
                runnable = MultiMessageTask(),
                cardId = card?.cardId,
                initialMessage = initialMessage,
            ) { postUi { handleCommandResult(it) } }
        }

        binding.themeView.chipGroupTheme.fitChipsByGroupWidth()
        binding.themeView.chipGroupTheme.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.chipThemeLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.chipThemeDark -> AppCompatDelegate.MODE_NIGHT_YES
                R.id.chipThemeSystem -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                else -> null
            }

            if (mode != null) {
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }

        binding.cardView.sliderWallet.stepSize = 1f
        binding.cardView.tvWalletPubKey.setOnClickListener {
            requireContext().copyToClipboard(binding.cardView.tvWalletPubKey.text)
            showToast("PubKey copied to clipboard")
        }
    }

    private fun sign(strategyType: SignStrategyType) {
        val userHexHash = binding.signView.etHashesToSign.text.toString()
        val strategy = when (strategyType) {
            SignStrategyType.SINGLE -> {
                SingleSignStrategy(userHexHash, ::signHash, ::prepareHashesToSign)
            }
            SignStrategyType.MULTIPLE -> {
                MultiplySignStrategy(userHexHash, ::signHashes, ::prepareHashesToSign)
            }
        }
        strategy.onError = { showToast(it) }
        strategy.execute()
    }

    override fun handleCommandResult(result: CompletionResult<*>) {
        when (result) {
            is CompletionResult.Success -> {
                val json = jsonConverter.prettyPrint(result.data)
                showDialog(json)

                if (hasSavedUserCodes()) {
                    binding.setPinView.userCodeRepositoryContainer.isVisible = true
                    binding.setPinView.btnDeleteUserCode.isVisible = hasSavedUserCodeForScannedCard()
                } else {
                    binding.setPinView.userCodeRepositoryContainer.isVisible = false
                }
                if (hasSavedCardTokens()) {
                    binding.setPinView.cardTokensRepositoryContainer.isVisible = true
                    binding.setPinView.btnDeleteAccessToken.isVisible = hasSavedCardTokens()
                } else {
                    binding.setPinView.cardTokensRepositoryContainer.isVisible = false
                }
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

    override fun onCardChanged(card: Card?) {
        postUi { updateWalletsSlider() }
    }

    private fun updateWalletsSlider() {
        fun updateWalletInfoContainerVisibility(visibility: Int) {
            if (visibility == View.VISIBLE) binding.cardView.flCardContainer.beginDelayedTransition()
            binding.cardView.walletInfoContainer.visibility = visibility
        }
        binding.cardView.sliderWallet.removeOnSliderTouchListener(sliderTouchListener)

        if (walletsCount == 0) {
            updateWalletInfoContainerVisibility(View.GONE)
            selectedIndexOfWallet = -1
            return
        }

        binding.cardView.sliderWallet.post {
            if (walletsCount == 1) {
                selectedIndexOfWallet = 0
                updateWalletInfoContainerVisibility(View.VISIBLE)
                binding.cardView.sliderWallet.value = 0f
                binding.cardView.sliderWallet.valueTo = 1f
                binding.cardView.sliderWallet.isEnabled = false
                updateWalletInfo()
                return@post
            }

            updateWalletInfoContainerVisibility(View.VISIBLE)
            binding.cardView.sliderWallet.isEnabled = true
            binding.cardView.sliderWallet.valueFrom = 0f
            binding.cardView.sliderWallet.valueTo = walletsCount - 1f

            if (selectedIndexOfWallet == -1 || selectedIndexOfWallet >= walletsCount) {
                selectedIndexOfWallet = 0
            }
            binding.cardView.sliderWallet.value = selectedIndexOfWallet.toFloat()
            binding.cardView.sliderWallet.addOnSliderTouchListener(sliderTouchListener)
            updateWalletInfo()
        }
    }

    private fun updateWalletInfo() {
        binding.cardView.tvWalletsCount.text = "$walletsCount"
        binding.cardView.tvWalletIndex.text = "$selectedIndexOfWallet"
        binding.cardView.tvWalletCurve.text = "${selectedWallet?.curve}"
        binding.cardView.tvWalletPubKey.text = "${selectedWallet?.publicKey?.toHexString()}"
    }
}
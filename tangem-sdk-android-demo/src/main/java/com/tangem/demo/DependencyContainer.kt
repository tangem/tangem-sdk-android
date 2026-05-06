package com.tangem.demo

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.tangem.LogFormat
import com.tangem.SessionViewDelegate
import com.tangem.TangemSdk
import com.tangem.TangemSdkLogger
import com.tangem.ViewDelegateMessage
import com.tangem.WrongValueType
import com.tangem.common.UserCodeType
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.Config
import com.tangem.common.core.ProductType
import com.tangem.common.core.TangemError
import com.tangem.common.card.EllipticCurve
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.extensions.VoidCallback
import com.tangem.common.services.secure.SecureStorage
import com.tangem.crypto.bip39.Wordlist
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.operations.resetcode.ResetCodesViewDelegate
import com.tangem.sdk.DefaultSessionViewDelegate
import com.tangem.sdk.extensions.createLogger
import com.tangem.sdk.extensions.getWordlist
import com.tangem.sdk.extensions.initAuthenticationManager
import com.tangem.sdk.extensions.initKeystoreManager
import com.tangem.sdk.nfc.AndroidNfcAvailabilityProvider
import com.tangem.sdk.nfc.NfcManager
import com.tangem.sdk.storage.create
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DependencyContainer(context: Context) {

    val config: Config = Config().apply {
        linkedTerminal = false
        filter.allowedCardTypes = FirmwareVersion.FirmwareType.values().toList()
        defaultDerivationPaths = mutableMapOf(
            EllipticCurve.Secp256k1 to listOf(
                DerivationPath(rawPath = "m/44/0"),
                DerivationPath(rawPath = "m/44/1"),
            ),
        )
    }

    val logger: TangemSdkLogger = TangemSdk.createLogger(LogFormat.StairsFormatter())

    private val secureStorage: SecureStorage = SecureStorage.create(context)
    private val wordlist: Wordlist = Wordlist.getWordlist()
    private val nfcAvailabilityProvider = AndroidNfcAvailabilityProvider(context)

    private val nfcManager = NfcManager()
    private val proxyViewDelegate = ProxySessionViewDelegate()

    private var sdk: TangemSdk? = null
    private var boundActivity: FragmentActivity? = null

    val viewDelegate: SessionViewDelegate get() = proxyViewDelegate

    fun getSdk(activity: FragmentActivity): TangemSdk {
        bindActivity(activity)
        return sdk ?: createSdk(activity).also { sdk = it }
    }

    private fun bindActivity(activity: FragmentActivity) {
        if (boundActivity === activity) return

        boundActivity?.let { previous ->
            previous.lifecycle.removeObserver(nfcManager)
        }

        nfcManager.setCurrentActivity(activity)
        activity.lifecycle.addObserver(nfcManager)

        val delegate = DefaultSessionViewDelegate(nfcManager, activity).apply {
            sdkConfig = config
        }
        proxyViewDelegate.delegate = delegate

        boundActivity = activity
    }

    private fun createSdk(activity: FragmentActivity): TangemSdk {
        val authenticationManager = TangemSdk.initAuthenticationManager(activity)
        return TangemSdk(
            reader = nfcManager.reader,
            viewDelegate = proxyViewDelegate,
            nfcAvailabilityProvider = nfcAvailabilityProvider,
            secureStorage = secureStorage,
            wordlist = wordlist,
            config = config,
            authenticationManager = authenticationManager,
            keystoreManager = TangemSdk.initKeystoreManager(authenticationManager, secureStorage),
        )
    }

    /**
     * Proxy that delegates all [SessionViewDelegate] calls to the currently bound
     * [DefaultSessionViewDelegate], allowing the singleton [TangemSdk] to show dialogs
     * on whichever activity is currently active.
     */
    private class ProxySessionViewDelegate : SessionViewDelegate {

        var delegate: SessionViewDelegate? = null

        override val viewVisibility: StateFlow<Boolean>
            get() = delegate?.viewVisibility ?: MutableStateFlow(false)

        override val resetCodesViewDelegate: ResetCodesViewDelegate
            get() = delegate!!.resetCodesViewDelegate

        override suspend fun onSessionStarted(
            cardId: String?,
            message: ViewDelegateMessage?,
            enableHowTo: Boolean,
            iconScanRes: Int?,
            productType: ProductType,
        ) {
            delegate?.onSessionStarted(cardId, message, enableHowTo, iconScanRes, productType)
        }

        override fun onSecurityDelay(ms: Int, totalDurationSeconds: Int, productType: ProductType) {
            delegate?.onSecurityDelay(ms, totalDurationSeconds, productType)
        }

        override fun onDelay(total: Int, current: Int, step: Int, productType: ProductType) {
            delegate?.onDelay(total, current, step, productType)
        }

        override fun onTagLost(productType: ProductType) {
            delegate?.onTagLost(productType)
        }

        override fun onTagConnected() {
            delegate?.onTagConnected()
        }

        override fun onWrongCard(wrongValueType: WrongValueType) {
            delegate?.onWrongCard(wrongValueType)
        }

        override fun onSessionStopped(message: com.tangem.Message?, onDialogHidden: () -> Unit) {
            delegate?.onSessionStopped(message, onDialogHidden)
        }

        override fun onError(error: TangemError) {
            delegate?.onError(error)
        }

        override fun requestUserCode(
            type: UserCodeType,
            isFirstAttempt: Boolean,
            showForgotButton: Boolean,
            cardId: String?,
            callback: CompletionCallback<String>,
        ) {
            delegate?.requestUserCode(type, isFirstAttempt, showForgotButton, cardId, callback)
        }

        override fun showWelcomeBackWarning(callback: CompletionCallback<Unit>) {
            delegate?.showWelcomeBackWarning(callback)
        }

        override fun requestUserCodeChange(type: UserCodeType, cardId: String?, callback: CompletionCallback<String>) {
            delegate?.requestUserCodeChange(type, cardId, callback)
        }

        override fun setConfig(config: Config) {
            delegate?.setConfig(config)
        }

        override fun setMessage(message: ViewDelegateMessage?) {
            delegate?.setMessage(message)
        }

        override fun dismiss() {
            delegate?.dismiss()
        }

        override fun showHealthAlert(onContinue: () -> Unit) {
            delegate?.showHealthAlert(onContinue)
        }

        override fun attestationDidFail(isDevCard: Boolean, positive: VoidCallback, negative: VoidCallback) {
            delegate?.attestationDidFail(isDevCard, positive, negative)
        }

        override fun attestationCompletedOffline(positive: VoidCallback, negative: VoidCallback, retry: VoidCallback) {
            delegate?.attestationCompletedOffline(positive, negative, retry)
        }

        override fun attestationCompletedWithWarnings(positive: VoidCallback) {
            delegate?.attestationCompletedWithWarnings(positive)
        }
    }
}
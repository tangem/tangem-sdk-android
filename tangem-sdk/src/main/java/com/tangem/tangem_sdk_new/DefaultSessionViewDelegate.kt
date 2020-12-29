package com.tangem.tangem_sdk_new

import android.app.Activity
import android.os.Build
import android.view.ContextThemeWrapper
import com.tangem.*
import com.tangem.commands.PinType
import com.tangem.tangem_sdk_new.nfc.NfcAntennaLocationProvider
import com.tangem.tangem_sdk_new.nfc.NfcManager
import com.tangem.tangem_sdk_new.ui.NfcSessionDialog

/**
 * Default implementation of [SessionViewDelegate].
 * If no customisation is required, this is the preferred way to use Tangem SDK.
 */
class DefaultSessionViewDelegate(
    private val nfcManager: NfcManager
) : SessionViewDelegate {

    var sdkConfig: Config? = null

    lateinit var activity: Activity
    private var readingDialog: NfcSessionDialog? = null

    override fun onSessionStarted(cardId: String?, message: Message?, enableHowTo: Boolean) {
        postUI {
            if (readingDialog == null) createReadingDialog(activity)
            readingDialog?.enableHowTo(enableHowTo)
            readingDialog?.show(SessionViewDelegateState.Ready(formatCardId(cardId), message))
        }
    }

    private fun createReadingDialog(activity: Activity) {
        val nfcLocationProvider = NfcAntennaLocationProvider(Build.DEVICE)
        val themeWrapper = ContextThemeWrapper(activity, R.style.CardSdkTheme)
        readingDialog = NfcSessionDialog(themeWrapper, nfcManager, nfcLocationProvider).apply {
            setOwnerActivity(activity)
            dismissWithAnimation = true
            create()
            setOnCancelListener {
                nfcManager.reader.stopSession(true)
                createReadingDialog(activity)
            }
        }
    }

    override fun onSecurityDelay(ms: Int, totalDurationSeconds: Int) {
        postUI {
            readingDialog?.show(SessionViewDelegateState.SecurityDelay(ms, totalDurationSeconds))
        }
    }

    override fun onDelay(total: Int, current: Int, step: Int) {
        postUI {
            readingDialog?.show(SessionViewDelegateState.Delay(total, current, step))
        }
    }

    override fun onTagLost() {
        postUI { readingDialog?.show(SessionViewDelegateState.TagLost) }

    }

    override fun onTagConnected() {
        postUI { readingDialog?.show(SessionViewDelegateState.TagConnected) }
    }

    override fun onWrongCard(wrongValueType: WrongValueType) {
        postUI { readingDialog?.show(SessionViewDelegateState.WrongCard(wrongValueType)) }
    }

    override fun onSessionStopped(message: Message?) {
        postUI { readingDialog?.show(SessionViewDelegateState.Success(message)) }
    }

    override fun onError(error: TangemError) {
        postUI { readingDialog?.show(SessionViewDelegateState.Error(error)) }
    }

    override fun onPinRequested(pinType: PinType, callback: (pin: String) -> Unit) {
        postUI { readingDialog?.show(SessionViewDelegateState.PinRequested(pinType, callback)) }
    }

    override fun onPinChangeRequested(pinType: PinType, callback: (pin: String) -> Unit) {
        postUI {
            if (readingDialog == null) {
                createReadingDialog(activity)
            }
            readingDialog?.enableHowTo(false)
            readingDialog?.show(SessionViewDelegateState.PinChangeRequested(pinType, callback))
        }
    }

    override fun setConfig(config: Config) {
        sdkConfig = config
    }

    override fun setMessage(message: Message?) {
        readingDialog?.setMessage(message)
    }

    override fun dismiss() {
        postUI { readingDialog?.dismiss() }
    }

    private fun formatCardId(cardId: String?): String? {
        val cardId = cardId ?: return null
        val displayedNumbersCount = sdkConfig?.cardIdDisplayedNumbersCount ?: return cardId

        return cardId.dropLast(1).takeLast(displayedNumbersCount)
    }

    companion object {
        fun createLogger(): LoggerInterface {
            return object : LoggerInterface {
                override fun i(logTag: String, message: String) {
                    android.util.Log.i(logTag, message)
                }

                override fun e(logTag: String, message: String) {
                    android.util.Log.e(logTag, message)
                }

                override fun v(logTag: String, message: String) {
                    android.util.Log.v(logTag, message)
                }

                override fun write(message: LogMessage) {
                    when (message.type) {
                        MessageType.ERROR -> e(message.type.name, message.message)
                        else -> i(message.type.name, message.message)
                    }
                }
            }
        }
    }
}
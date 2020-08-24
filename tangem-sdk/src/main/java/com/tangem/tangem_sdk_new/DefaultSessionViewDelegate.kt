package com.tangem.tangem_sdk_new

import android.app.Activity
import android.view.ContextThemeWrapper
import com.tangem.*
import com.tangem.commands.PinType
import com.tangem.tangem_sdk_new.nfc.NfcReader
import com.tangem.tangem_sdk_new.ui.NfcSessionDialog

/**
 * Default implementation of [SessionViewDelegate].
 * If no customisation is required, this is the preferred way to use Tangem SDK.
 */
class DefaultSessionViewDelegate(private val reader: NfcReader) : SessionViewDelegate {

    lateinit var activity: Activity
    private var readingDialog: NfcSessionDialog? = null

    init {
        setLogger()
    }

    override fun onSessionStarted(cardId: String?, message: Message?) {
        postUI {
            if (readingDialog == null) createReadingDialog(activity)
            readingDialog?.show(SessionViewDelegateState.Ready(cardId, message))
        }
    }

    private fun createReadingDialog(activity: Activity) {
        readingDialog = NfcSessionDialog(ContextThemeWrapper(activity, R.style.CardSdkTheme)).apply {
            dismissWithAnimation = true
            create()
            setOnCancelListener {
                reader.stopSession(true)
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
        postUI { readingDialog?.show(SessionViewDelegateState.PinChangeRequested(pinType, callback)) }
    }

    private fun setLogger() {
        Log.setLogger(
            object : LoggerInterface {
                override fun i(logTag: String, message: String) {
                    android.util.Log.i(logTag, message)
                }

                override fun e(logTag: String, message: String) {
                    android.util.Log.e(logTag, message)
                }

                override fun v(logTag: String, message: String) {
                    android.util.Log.v(logTag, message)
                }
            }
        )
    }
}
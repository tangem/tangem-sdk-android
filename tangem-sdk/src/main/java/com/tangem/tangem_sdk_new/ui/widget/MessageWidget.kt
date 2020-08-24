package com.tangem.tangem_sdk_new.ui.widget

import android.view.View
import android.widget.TextView
import com.tangem.TangemError
import com.tangem.TangemSdkError
import com.tangem.WrongValueType
import com.tangem.tangem_sdk_new.R
import com.tangem.tangem_sdk_new.SessionViewDelegateState
import com.tangem.tangem_sdk_new.extensions.localizedDescription
import com.tangem.tangem_sdk_new.postUI

/**
[REDACTED_AUTHOR]
 */
class MessageWidget(mainView: View) : BaseSessionDelegateStateWidget(mainView) {

    private val tvTaskTitle: TextView = mainView.findViewById(R.id.tvTaskTitle)
    private val tvTaskMessage: TextView = mainView.findViewById(R.id.tvTaskMessage)

    override fun setState(params: SessionViewDelegateState) {
        when (params) {
            is SessionViewDelegateState.Ready -> {
                setText(tvTaskTitle, params.message?.header, R.string.dialog_ready_to_scan)
                setText(tvTaskMessage, params.message?.body, R.string.dialog_scan_text)
            }
            is SessionViewDelegateState.Success -> {
                setText(tvTaskTitle, params.message?.header, R.string.dialog_success)
                setText(tvTaskMessage, params.message?.body ?: "")
            }
            is SessionViewDelegateState.Error -> {
                setText(tvTaskTitle, null, R.string.dialog_error)

                val errorMessage = getErrorMessage(params.error)
                val formattedErrorMessage = mainView.context.getString(
                        R.string.error_message,
                        params.error.code.toString(),
                        errorMessage
                )
                setText(tvTaskMessage, formattedErrorMessage)
            }
            is SessionViewDelegateState.SecurityDelay -> {
                setText(tvTaskTitle, null, R.string.dialog_security_delay)
                setText(tvTaskMessage, null, R.string.dialog_security_delay_description)
            }
            is SessionViewDelegateState.Delay -> {
                setText(tvTaskTitle, null, R.string.dialog_delay)
                setText(tvTaskMessage, null, R.string.dialog_delay_description)
            }
            is SessionViewDelegateState.PinRequested -> {
            }
            is SessionViewDelegateState.PinChangeRequested -> {
            }
            is SessionViewDelegateState.TagLost -> {
                setText(tvTaskTitle, null, R.string.dialog_ready_to_scan)
                setText(tvTaskMessage, null, R.string.dialog_scan_text)
            }
            is SessionViewDelegateState.TagConnected -> {
            }
            is SessionViewDelegateState.WrongCard -> {
                val description = when (params.wrongValueType) {
                    WrongValueType.CardId -> getString(R.string.error_wrong_card_number)
                    WrongValueType.CardType -> getString(R.string.error_wrong_card_type)

                }

                setText(tvTaskTitle, null, R.string.dialog_error)
                val bodyMessage = mainView.context.getString(
                        R.string.error_message,
                        getString(R.string.error_wrong_card),
                        description
                )

                setText(tvTaskMessage, bodyMessage)
                postUI(2000) {
                    setState(SessionViewDelegateState.Ready(null, null))
                }
            }
        }
    }

    private fun getErrorMessage(error: TangemError): String = if (error is TangemSdkError) {
        getString(error.localizedDescription())
    } else {
        error.customMessage
    }

    private fun setText(tv: TextView, text: String?, id: Int? = null) {
        text?.let {
            tv.text = it
            return
        }
        id?.let { tv.text = getString(it) }
    }
}
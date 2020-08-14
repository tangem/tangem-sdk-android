package com.tangem.tangem_sdk_new.ui

import android.app.Activity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.tangem.Log
import com.tangem.tangem_sdk_new.R
import com.tangem.tangem_sdk_new.SessionViewDelegateState
import com.tangem.tangem_sdk_new.postUI
import com.tangem.tangem_sdk_new.ui.widget.*
import com.tangem.tangem_sdk_new.ui.widget.progressBar.ProgressbarStateWidget
import kotlinx.android.synthetic.main.bottom_sheet_layout.*

class NfcSessionDialog(val activity: Activity) : BottomSheetDialog(activity) {
    private val tag = this::class.java.simpleName

    private lateinit var mainContentView: View

    private lateinit var headerWidget: HeaderWidget
    private lateinit var touchCardWidget: TouchCardWidget
    private lateinit var progressStateWidget: ProgressbarStateWidget
    private lateinit var pinCodeRequestWidget: PinCodeRequestWidget
    private lateinit var pinCodeSetChangeWidget: PinCodeModificationWidget
    private lateinit var messageWidget: MessageWidget

    private val stateWidgets = mutableListOf<StateWidget<*>>()

    private var currentState: SessionViewDelegateState? = null

    init {
        val dialogView = activity.layoutInflater.inflate(R.layout.bottom_sheet_layout, null)
        setContentView(dialogView)
    }

    override fun setContentView(view: View) {
        super.setContentView(view)

        mainContentView = view
        headerWidget = HeaderWidget(view.findViewById(R.id.llHeader))
        touchCardWidget = TouchCardWidget(view.findViewById(R.id.rlTouchCard))
        progressStateWidget = ProgressbarStateWidget(view.findViewById(R.id.clProgress))
        pinCodeRequestWidget = PinCodeRequestWidget(view.findViewById(R.id.csPinCode))
        pinCodeSetChangeWidget = PinCodeModificationWidget(view.findViewById(R.id.llChangePin), 0)
        messageWidget = MessageWidget(view.findViewById(R.id.llMessage))

        stateWidgets.add(headerWidget)
        stateWidgets.add(touchCardWidget)
        stateWidgets.add(progressStateWidget)
        stateWidgets.add(pinCodeRequestWidget)
        stateWidgets.add(pinCodeSetChangeWidget)
        stateWidgets.add(messageWidget)
    }

    fun show(state: SessionViewDelegateState) {
        if (!this.isShowing) {
            this.show()
        }
        when (state) {
            is SessionViewDelegateState.Ready -> onReady(state)
            is SessionViewDelegateState.Success -> onSuccess(state)
            is SessionViewDelegateState.Error -> onError(state)
            is SessionViewDelegateState.SecurityDelay -> onSecurityDelay(state)
            is SessionViewDelegateState.Delay -> onDelay(state)
            is SessionViewDelegateState.PinRequested -> onPinRequested(state)
            is SessionViewDelegateState.PinChangeRequested -> onPinChangeRequested(state)
            is SessionViewDelegateState.TagLost -> onTagLost(state)
            is SessionViewDelegateState.TagConnected -> onTagConnected(state)
            is SessionViewDelegateState.WrongCard -> onWrongCard(state)
        }
        currentState = state
    }

    private fun onReady(state: SessionViewDelegateState.Ready) {
        Log.i(tag, "onReady")
        setStateAndShow(state, headerWidget, touchCardWidget, messageWidget)
    }

    private fun onSuccess(state: SessionViewDelegateState.Success) {
        Log.i(tag, "onSuccess")
        setStateAndShow(state, progressStateWidget, messageWidget)
        performHapticFeedback()
        postUI(1500) { dismiss() }
    }

    private fun onError(state: SessionViewDelegateState.Error) {
        Log.i(tag, "onError")
        setStateAndShow(state, progressStateWidget, messageWidget)
        performHapticFeedback()
    }

    private fun onSecurityDelay(state: SessionViewDelegateState.SecurityDelay) {
        Log.i(tag, "onSecurityDelay")
        setStateAndShow(state, progressStateWidget, messageWidget)
        performHapticFeedback()
    }

    private fun onDelay(state: SessionViewDelegateState.Delay) {
        Log.i(tag, "onDelay")
        setStateAndShow(state, progressStateWidget, messageWidget)
        performHapticFeedback()
    }

    private fun onPinRequested(state: SessionViewDelegateState.PinRequested) {
        Log.i(tag, "onPinRequested")
        enableBottomSheetAnimation()
        setStateAndShow(state, pinCodeRequestWidget)

        pinCodeRequestWidget.onContinue = {
            enableBottomSheetAnimation()
            pinCodeRequestWidget.onContinue = null

            val readyState = SessionViewDelegateState.Ready(null, null)
            setStateAndShow(readyState, touchCardWidget, messageWidget)
            postUI(200) { state.callback(it) }
        }

        performHapticFeedback()
    }

    private fun onPinChangeRequested(state: SessionViewDelegateState.PinChangeRequested) {
        Log.i(tag, "onPinChangeRequested")
        enableBottomSheetAnimation()
        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        headerWidget.onClose = { cancel() }
        pinCodeSetChangeWidget.onSave = {
            enableBottomSheetAnimation()
            pinCodeSetChangeWidget.onSave = null

            val readyState = SessionViewDelegateState.Ready(null, null)
            setStateAndShow(readyState, headerWidget, touchCardWidget, messageWidget)
            state.callback(it)
        }

        setStateAndShow(state, headerWidget, pinCodeSetChangeWidget)
        performHapticFeedback()
    }

    private fun onTagLost(state: SessionViewDelegateState) {
        Log.i(tag, "onTagLost")
        if (currentState is SessionViewDelegateState.Success ||
            currentState is SessionViewDelegateState.PinRequested ||
            currentState is SessionViewDelegateState.PinChangeRequested) {
            return
        }
        setStateAndShow(state, touchCardWidget, messageWidget)
    }

    private fun onTagConnected(state: SessionViewDelegateState) {
        Log.i(tag, "onTagConnected")
        setStateAndShow(state, progressStateWidget, messageWidget)
    }

    private fun onWrongCard(state: SessionViewDelegateState) {
        Log.i(tag, "onWrongCard")
        if (currentState !is SessionViewDelegateState.WrongCard) {
            performHapticFeedback()
            setStateAndShow(state, progressStateWidget, messageWidget)
            progressStateWidget.setState(state)
            messageWidget.setState(state)
            postUI(2000) {
                val readyState = SessionViewDelegateState.Ready(null, null)
                setStateAndShow(readyState, touchCardWidget, messageWidget)
            }
        }
    }

    private fun setStateAndShow(state: SessionViewDelegateState, vararg views: StateWidget<SessionViewDelegateState>) {
        views.forEach { it.setState(state) }

        val toHide = stateWidgets.filter { !views.contains(it) && it.isVisible() }
        val toShow = views.filter { !it.isVisible() }

        toHide.forEach { it.showWidget(false) }
        toShow.forEach { it.showWidget(true) }
    }

    private fun performHapticFeedback() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            llHeader?.isHapticFeedbackEnabled = true
            llHeader?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    private fun enableBottomSheetAnimation(){
        val dialogContainer = delegate.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.parent
        dialogContainer.let { TransitionManager.beginDelayedTransition(it as ViewGroup, AutoTransition()) }
    }

    override fun dismiss() {
        stateWidgets.forEach { it.onBottomSheetDismiss() }
        super.dismiss()
    }
}
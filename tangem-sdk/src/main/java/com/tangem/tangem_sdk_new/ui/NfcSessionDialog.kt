package com.tangem.tangem_sdk_new.ui

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.transition.TransitionManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.tangem.Log
import com.tangem.Message
import com.tangem.tangem_sdk_new.R
import com.tangem.tangem_sdk_new.SessionViewDelegateState
import com.tangem.tangem_sdk_new.extensions.hide
import com.tangem.tangem_sdk_new.extensions.show
import com.tangem.tangem_sdk_new.nfc.NfcLocationProvider
import com.tangem.tangem_sdk_new.nfc.NfcManager
import com.tangem.tangem_sdk_new.postUI
import com.tangem.tangem_sdk_new.ui.widget.*
import com.tangem.tangem_sdk_new.ui.widget.howTo.HowToTapWidget
import com.tangem.tangem_sdk_new.ui.widget.progressBar.ProgressbarStateWidget
import kotlinx.android.synthetic.main.bottom_sheet_layout.*

class NfcSessionDialog(
    context: Context,
    private val nfcManager: NfcManager,
    private val nfcLocationProvider: NfcLocationProvider,
) : BottomSheetDialog(context) {
    private val tag = this::class.java.simpleName

    private lateinit var taskContainer: ViewGroup
    private lateinit var howToContainer: ViewGroup

    private lateinit var headerWidget: HeaderWidget
    private lateinit var touchCardWidget: TouchCardWidget
    private lateinit var progressStateWidget: ProgressbarStateWidget
    private lateinit var pinCodeRequestWidget: PinCodeRequestWidget
    private lateinit var pinCodeSetChangeWidget: PinCodeModificationWidget
    private lateinit var messageWidget: MessageWidget
    private lateinit var howToTapWidget: HowToTapWidget

    private val stateWidgets = mutableListOf<StateWidget<*>>()

    private var currentState: SessionViewDelegateState? = null

    init {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_layout, null)
        setContentView(dialogView)
    }

    override fun setContentView(view: View) {
        super.setContentView(view)

        val nfcLocation = nfcLocationProvider.getLocation() ?: NfcLocation.model13

        taskContainer = view.findViewById(R.id.taskContainer)
        howToContainer = view.findViewById(R.id.howToContainer)

        headerWidget = HeaderWidget(view.findViewById(R.id.llHeader))
        touchCardWidget = TouchCardWidget(view.findViewById(R.id.rlTouchCard), nfcLocation)
        progressStateWidget = ProgressbarStateWidget(view.findViewById(R.id.clProgress))
        pinCodeRequestWidget = PinCodeRequestWidget(view.findViewById(R.id.csPinCode))
        pinCodeSetChangeWidget = PinCodeModificationWidget(view.findViewById(R.id.llChangePin), 0)
        messageWidget = MessageWidget(view.findViewById(R.id.llMessage))
        howToTapWidget = HowToTapWidget(howToContainer, nfcManager, nfcLocationProvider)

        stateWidgets.add(headerWidget)
        stateWidgets.add(touchCardWidget)
        stateWidgets.add(progressStateWidget)
        stateWidgets.add(pinCodeRequestWidget)
        stateWidgets.add(pinCodeSetChangeWidget)
        stateWidgets.add(messageWidget)
        stateWidgets.add(howToTapWidget)

        headerWidget.onHowTo = { show(SessionViewDelegateState.HowToTap) }

        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    fun enableHowTo(enable: Boolean) {
        headerWidget.howToIsEnabled = enable
    }

    fun setMessage(message: Message?) {
        messageWidget.setMessage(message)
    }

    fun show(state: SessionViewDelegateState) {
        if (!this.isShowing) this.show()
        when (state) {
            is SessionViewDelegateState.Ready -> onReady(state)
            is SessionViewDelegateState.Success -> onSuccess(state)
            is SessionViewDelegateState.Error -> onError(state)
            is SessionViewDelegateState.SecurityDelay -> onSecurityDelay(state)
            is SessionViewDelegateState.Delay -> onDelay(state)
            is SessionViewDelegateState.PinRequested -> onPinRequested(state)
            is SessionViewDelegateState.PinChangeRequested -> onPinChangeRequested(state)
            is SessionViewDelegateState.WrongCard -> onWrongCard(state)
            SessionViewDelegateState.TagConnected -> onTagConnected(state)
            SessionViewDelegateState.TagLost -> onTagLost(state)
            SessionViewDelegateState.HowToTap -> howToTap(state)
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
        postUI(1000) { cancel() }
    }

    private fun onError(state: SessionViewDelegateState.Error) {
        Log.i(tag, "onError")
        setStateAndShow(state, progressStateWidget, messageWidget)
        performHapticFeedback()
    }

    private fun onSecurityDelay(state: SessionViewDelegateState.SecurityDelay) {
        Log.i(tag, "onSecurityDelay: ${state.ms}, ${state.totalDurationSeconds}")
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
        if (pinCodeRequestWidget.canExpand()) {
            headerWidget.isFullScreenMode = true
            headerWidget.onClose = { cancel() }
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        pinCodeRequestWidget.onContinue = {
            enableBottomSheetAnimation()
            pinCodeRequestWidget.onContinue = null
            headerWidget.isFullScreenMode = false

            setStateAndShow(getEmptyOnReadyEvent(), headerWidget, touchCardWidget, messageWidget)
            postUI(200) { state.callback(it) }
        }

        setStateAndShow(state, headerWidget, pinCodeRequestWidget)
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

            setStateAndShow(getEmptyOnReadyEvent(), headerWidget, touchCardWidget, messageWidget)
            postUI(200) { state.callback(it) }
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
                setStateAndShow(getEmptyOnReadyEvent(), touchCardWidget, messageWidget)
            }
        }
    }

    private fun howToTap(state: SessionViewDelegateState) {
        enableBottomSheetAnimation()
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        taskContainer.hide()
        findDesignBottomSheetView()?.let { it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT }
        howToContainer.show()

        howToTapWidget.previousState = currentState
        howToTapWidget.onCloseListener = {
            howToTapWidget.onCloseListener = null
            enableBottomSheetAnimation()
            howToContainer.hide()
            findDesignBottomSheetView()?.let { it.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT }
            taskContainer.show()

            howToTapWidget.previousState?.let {
                howToTapWidget.setState(it)
                show(it)
            }
            howToTapWidget.previousState = null
        }
        setStateAndShow(state, howToTapWidget)
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

    private fun getEmptyOnReadyEvent(): SessionViewDelegateState {
        return SessionViewDelegateState.Ready(headerWidget.cardId, null)
    }

    private fun enableBottomSheetAnimation() {
        (findDesignBottomSheetView()?.parent as? ViewGroup)?.let {
            TransitionManager.beginDelayedTransition(it)
        }
    }

    private fun findDesignBottomSheetView(): View? {
        return delegate.findViewById(com.google.android.material.R.id.design_bottom_sheet)
    }

    override fun dismiss() {
        stateWidgets.forEach { it.onBottomSheetDismiss() }
        super.dismiss()
    }
}
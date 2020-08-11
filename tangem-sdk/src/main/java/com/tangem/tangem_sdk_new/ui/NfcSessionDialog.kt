package com.tangem.tangem_sdk_new.ui

import android.app.Activity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.tangem.tangem_sdk_new.R
import com.tangem.tangem_sdk_new.SessionViewDelegateState
import com.tangem.tangem_sdk_new.extensions.show
import com.tangem.tangem_sdk_new.postUI
import com.tangem.tangem_sdk_new.ui.widget.*
import com.tangem.tangem_sdk_new.ui.widget.progressBar.ProgressbarStateWidget
import com.tangem.tangem_sdk_new.ui.widget.progressBar.StateWidget
import kotlinx.android.synthetic.main.bottom_sheet_layout.*
import kotlinx.android.synthetic.main.touch_card_layout.*
import ru.gbixahue.eu4d.core.log.Log

class NfcSessionDialog(val activity: Activity) : BottomSheetDialog(activity, R.style.SdkBottomSheetDialogStyle) {

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
        if (currentState is SessionViewDelegateState.PinChangeRequested) {
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        currentState = state
    }

    private fun onReady(state: SessionViewDelegateState.Ready) {
        Log.d(this, "onReady")
        headerWidget.apply(state)

        showViews(touchCardWidget, messageWidget)
        messageWidget.apply(state)
    }

    private fun onSuccess(state: SessionViewDelegateState.Success) {
        Log.d(this, "onSuccess")
        showViews(progressStateWidget, messageWidget)
        messageWidget.apply(state)
        progressStateWidget.apply(state)
        performHapticFeedback()
        postUI(1500) { dismiss() }
    }

    private fun onError(state: SessionViewDelegateState.Error) {
        Log.d(this, "onError")
        showViews(progressStateWidget, messageWidget)
        messageWidget.apply(state)
        progressStateWidget.apply(state)
        performHapticFeedback()
    }

    private fun onSecurityDelay(state: SessionViewDelegateState.SecurityDelay) {
        Log.d(this, "onSecurityDelay")
//        show(flSecurityDelay)
        showViews(progressStateWidget, messageWidget)
        messageWidget.apply(state)
        progressStateWidget.apply(state)
        performHapticFeedback()
    }

    private fun onDelay(state: SessionViewDelegateState.Delay) {
        Log.d(this, "onDelay")
        showViews(progressStateWidget, messageWidget)
        messageWidget.apply(state)
        progressStateWidget.apply(state)

        performHapticFeedback()
    }

    private fun onPinRequested(state: SessionViewDelegateState.PinRequested) {
        Log.d(this, "onPinRequested")
        showViews(pinCodeRequestWidget)

        pinCodeRequestWidget.apply(state)
        pinCodeRequestWidget.onSave = {
            state.callback(it)
            pinCodeRequestWidget.onSave = null
            showViews(touchCardWidget, messageWidget)
            messageWidget.apply(SessionViewDelegateState.Ready("", null))
        }

        performHapticFeedback()
    }

    private fun onPinChangeRequested(state: SessionViewDelegateState.PinChangeRequested) {
        Log.d(this, "onPinChangeRequested")
        showViews(pinCodeSetChangeWidget)

        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        headerWidget.onClose = {
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            postUI(150) { dismiss() }
        }
        pinCodeSetChangeWidget.onSave = {
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            pinCodeSetChangeWidget.onSave = null
            showViews(touchCardWidget, messageWidget)
            messageWidget.apply(SessionViewDelegateState.Ready("", null))
            state.callback(it)
        }

        headerWidget.apply(state)
        pinCodeSetChangeWidget.apply(state)
        performHapticFeedback()
    }

    private fun onTagLost(state: SessionViewDelegateState) {
        Log.d(this, "onTagLost")
        if (currentState is SessionViewDelegateState.Success ||
            currentState is SessionViewDelegateState.PinRequested ||
            currentState is SessionViewDelegateState.PinChangeRequested) {
            return
        }
        showViews(touchCardWidget, messageWidget)
        messageWidget.apply(state)
    }

    private fun onTagConnected(state: SessionViewDelegateState) {
        Log.d(this, "onTagConnected")
        showViews(progressStateWidget, messageWidget)
        progressStateWidget.apply(state)
    }

    private fun onWrongCard(state: SessionViewDelegateState) {
        Log.d(this, "onWrongCard")
        if (currentState !is SessionViewDelegateState.WrongCard) {
            performHapticFeedback()
            showViews(progressStateWidget, messageWidget)
            progressStateWidget.apply(state)
            messageWidget.apply(state)
            postUI(2000) {
                showViews(touchCardWidget, messageWidget)
                messageWidget.apply(SessionViewDelegateState.Ready("", null))
            }
        }
    }

    private fun showViews(vararg views: StateWidget<*>) {
        val toHide = stateWidgets.filter { !views.contains(it) && it.getView().visibility != View.GONE }
        val toShow = views.filter { it.getView().visibility != View.VISIBLE }

        if (toHide.isNotEmpty() || toShow.isNotEmpty()) {
            (mainContentView as? ViewGroup)?.let { TransitionManager.beginDelayedTransition(it, AutoTransition()) }
        }
        toHide.forEach { it.getView().show(false) }
        views.forEach { it.getView().show(true) }

        if (views.contains(touchCardWidget)) {
            rippleBackgroundNfc?.startRippleAnimation()
            val nfcDeviceAntenna = TouchCardAnimation(
                activity, ivHandCardHorizontal, ivHandCardVertical, llHand, llNfc
            )
            nfcDeviceAntenna.init()
        }
    }

    private fun performHapticFeedback() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            llHeader?.isHapticFeedbackEnabled = true
            llHeader?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

}
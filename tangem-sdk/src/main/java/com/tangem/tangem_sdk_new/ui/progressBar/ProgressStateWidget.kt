package com.tangem.tangem_sdk_new.ui.progressBar

import android.content.Context
import android.graphics.drawable.AnimatedVectorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.transition.AutoTransition
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.tangem.Log
import com.tangem.tangem_sdk_new.R
import com.tangem.tangem_sdk_new.SessionViewDelegateState
import kotlin.random.Random

/**
[REDACTED_AUTHOR]
 */
class ProgressbarStateWidget(
        private val context: Context,
        private val mainView: ViewGroup
) {

    private var currentState: SessionViewDelegateState? = null

    init {
        NoneState(mainView)
    }

    fun show(state: SessionViewDelegateState) {
        val viewState = when (state) {
            is SessionViewDelegateState.Ready -> IndeterminateProgressState(mainView)
            is SessionViewDelegateState.SecurityDelay -> DelayState(mainView)
            is SessionViewDelegateState.Success -> SuccessState(mainView)
            is SessionViewDelegateState.Error -> ErrorState(mainView)
            is SessionViewDelegateState.PinRequested -> WarningState(mainView)
//            is SessionViewDelegateState.Delay -> ErrorState(mainView)
//            is SessionViewDelegateState.PinChangeRequested -> ErrorState(mainView)
//            is SessionViewDelegateState.WrongCard -> ErrorState(mainView)
//            is SessionViewDelegateState.TagLost -> ErrorState(mainView)
//            is SessionViewDelegateState.TagConnected -> ErrorState(mainView)
            else -> NoneState(mainView)
        }
        viewState.apply(currentState ?: state, state)
        currentState = state
    }

}

interface State {
    fun apply(prevState: SessionViewDelegateState, state: SessionViewDelegateState)
}

abstract class BaseState(protected val mainView: ViewGroup) : State {
    protected val tag = "States"

    protected val progressBar: SdkProgressBar = mainView.findViewById(R.id.progressBar)
    protected val tvProgressValue: TextView = mainView.findViewById(R.id.tvProgressValue)

    protected val doneView: ImageView = mainView.findViewById(R.id.imvSuccess)
    protected val errorView: ImageView = mainView.findViewById(R.id.imvError)
    protected val warningView: ImageView = mainView.findViewById(R.id.imvWarning)


    init {
        hide(tvProgressValue)
        hide(progressBar)
        hide(doneView)
        hide(errorView)
        hide(warningView)
    }

    protected fun setMaxProgress() {
        setProgress(100f)
    }

    protected fun setProgress(progress: Float) {
        progressBar.isIndeterminate = false
        progressBar.setProgressWithAnimation(progress, 300)
        tvProgressValue.text = progress.toInt().toString()
    }

    protected fun show(view: View) {
        if (view.visibility == View.VISIBLE) return
        view.visibility = View.VISIBLE
    }

    protected fun hide(view: View) {
        if (view.visibility == View.GONE) return
        view.visibility = View.GONE
    }

    protected fun beginDelayedTransition(transition: Transition = AutoTransition()) {
        TransitionManager.beginDelayedTransition(mainView, transition)
    }
}

class NoneState(mainView: ViewGroup
) : BaseState(mainView) {
    override fun apply(prevState: SessionViewDelegateState, state: SessionViewDelegateState) {
        beginDelayedTransition()
        hide(progressBar)
        hide(tvProgressValue)
        hide(doneView)
        hide(errorView)
        hide(warningView)
    }
}

class SuccessState(mainView: ViewGroup) : BaseState(mainView) {
    override fun apply(prevState: SessionViewDelegateState, state: SessionViewDelegateState) {
        hide(tvProgressValue)
        hide(progressBar)
        hide(errorView)
        hide(warningView)

        show(progressBar)
        setMaxProgress()
        show(doneView)
        val drawable = doneView.drawable as? AnimatedVectorDrawable ?: return
        drawable.start()
    }
}


class ErrorState(mainView: ViewGroup) : BaseState(mainView) {
    override fun apply(prevState: SessionViewDelegateState, state: SessionViewDelegateState) {
        hide(tvProgressValue)
        hide(warningView)
        hide(doneView)

        show(progressBar)
        setMaxProgress()
        beginDelayedTransition()
        show(errorView)
    }
}

class WarningState(mainView: ViewGroup) : BaseState(mainView) {
    override fun apply(prevState: SessionViewDelegateState, state: SessionViewDelegateState) {
        hide(tvProgressValue)
        hide(errorView)
        hide(doneView)

        show(progressBar)
        setMaxProgress()
        beginDelayedTransition()
        show(warningView)
    }
}

class IndeterminateProgressState(mainView: ViewGroup) : BaseState(mainView) {
    override fun apply(prevState: SessionViewDelegateState, state: SessionViewDelegateState) {
        hide(tvProgressValue)
        hide(doneView)
        hide(errorView)
        hide(warningView)

        beginDelayedTransition()
        show(progressBar)
        progressBar.isIndeterminate = true
    }
}

class DelayState(mainView: ViewGroup) : BaseState(mainView) {
    override fun apply(prevState: SessionViewDelegateState, state: SessionViewDelegateState) {
        val delay = state as? SessionViewDelegateState.SecurityDelay ?: return

        hide(doneView)
        hide(errorView)
        hide(warningView)

        show(progressBar)
        beginDelayedTransition()
        show(tvProgressValue)
        progressBar.progressMax = delay.totalDurationSeconds.toFloat()
        setProgress((delay.ms / 1000).toFloat())
    }

    private fun calculateProgress(currentMs: Int, totalDelaySeconds: Int): Int {
        Log.i(tag, "currentMs: $currentMs, totalDelaySeconds: $totalDelaySeconds")
        return Random.nextInt(101)
    }
}
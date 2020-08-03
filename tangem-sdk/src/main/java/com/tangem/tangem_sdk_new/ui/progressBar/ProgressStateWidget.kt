package com.tangem.tangem_sdk_new.ui.progressBar

import android.graphics.drawable.AnimatedVectorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tangem.tangem_sdk_new.R
import com.tangem.tangem_sdk_new.SessionViewDelegateState

/**
[REDACTED_AUTHOR]
 */
interface StateWidget<P> {
    fun apply(params: P)
}

class ProgressbarStateWidget(private val mainView: ViewGroup) : StateWidget<SessionViewDelegateState> {

    init {
        NoneState(mainView)
    }

    override fun apply(params: SessionViewDelegateState) {
        val viewState = when (params) {
            is SessionViewDelegateState.Ready -> IndeterminateProgressState(mainView)
            is SessionViewDelegateState.SecurityDelay -> DelayState(mainView)
            is SessionViewDelegateState.Success -> SuccessState(mainView)
            is SessionViewDelegateState.Error -> ErrorState(mainView)
            is SessionViewDelegateState.PinRequested -> WarningState(mainView)
            is SessionViewDelegateState.TagLost -> NoneState(mainView)
            else -> NoneState(mainView)
        }
        viewState.apply(params)
    }
}

abstract class BaseState(protected val mainView: ViewGroup) : StateWidget<SessionViewDelegateState> {
    protected val tag = "States"

    protected val progressBar: SdkProgressBar = mainView.findViewById(R.id.progressBar)
    protected val tvProgressValue: TextView = mainView.findViewById(R.id.tvProgressValue)

    protected val doneView: ImageView = mainView.findViewById(R.id.imvSuccess)
    protected val exclamationView: ImageView = mainView.findViewById(R.id.imvExclamation)


    init {
        hide(tvProgressValue)
        hide(progressBar)
        hide(doneView)
        hide(exclamationView)
    }

    protected fun setMaxProgress() {
        setProgress(progressBar.progressMax)
    }

    protected fun setProgress(progress: Float) {
        if (progressBar.isIndeterminate) progressBar.isIndeterminate = false

        progressBar.setProgressWithAnimation(progress)
        tvProgressValue.text = progress.toInt().toString()
    }

    protected fun changeProgressColor(color: Int, withAnimation: Boolean = true) {
        val parsedColor = ContextCompat.getColor(progressBar.context, color)
        if (withAnimation) {
            progressBar.setProgressBarColorWithAnimation(parsedColor)
        } else {
            progressBar.progressBarColor = parsedColor
        }
    }

    protected fun changeExclamationColor(color: Int) {
        exclamationView.drawable.setTint(ContextCompat.getColor(progressBar.context, color))
    }

    protected fun show(view: View) {
        if (view.visibility == View.VISIBLE) return
        view.visibility = View.VISIBLE
    }

    protected fun hide(view: View) {
        if (view.visibility == View.GONE) return
        view.visibility = View.GONE
    }

    protected fun animateVectorDrawable(view: ImageView) {
        val drawable = view.drawable as? AnimatedVectorDrawable ?: return

        drawable.start()
    }
}

class NoneState(mainView: ViewGroup
) : BaseState(mainView) {
    override fun apply(params: SessionViewDelegateState) {
        hide(progressBar)
        hide(tvProgressValue)
        hide(doneView)
        hide(exclamationView)

        changeProgressColor(R.color.progress_bar_secondary_color, false)
        setProgress(0f)
    }
}

class SuccessState(mainView: ViewGroup) : BaseState(mainView) {
    override fun apply(params: SessionViewDelegateState) {
        hide(tvProgressValue)
        hide(progressBar)
        hide(exclamationView)

        show(progressBar)
        changeProgressColor(R.color.progress_bar_state_success_color)
        setMaxProgress()
        show(doneView)
        animateVectorDrawable(doneView)
    }
}


class ErrorState(mainView: ViewGroup) : BaseState(mainView) {
    override fun apply(params: SessionViewDelegateState) {
        hide(tvProgressValue)
        hide(doneView)

        val color = R.color.progress_bar_state_error_color
        show(progressBar)
        changeProgressColor(color)
        setMaxProgress()
        changeExclamationColor(color)
        show(exclamationView)
        animateVectorDrawable(exclamationView)
    }
}

class WarningState(mainView: ViewGroup) : BaseState(mainView) {
    override fun apply(params: SessionViewDelegateState) {
        hide(tvProgressValue)
        hide(doneView)

        val color = R.color.progress_bar_state_warning_color
        show(progressBar)
        changeProgressColor(color)
        setMaxProgress()
        changeExclamationColor(color)
        show(exclamationView)
        animateVectorDrawable(exclamationView)
    }
}

class IndeterminateProgressState(mainView: ViewGroup) : BaseState(mainView) {
    override fun apply(params: SessionViewDelegateState) {
        hide(tvProgressValue)
        hide(doneView)
        hide(exclamationView)

        changeProgressColor(R.color.progress_bar_color)
        show(progressBar)
        progressBar.isIndeterminate = true
    }
}

class DelayState(mainView: ViewGroup) : BaseState(mainView) {
    override fun apply(params: SessionViewDelegateState) {
        val delay = params as? SessionViewDelegateState.SecurityDelay ?: return

        hide(doneView)
        hide(exclamationView)

        show(progressBar)
        show(tvProgressValue)
        progressBar.progressMax = delay.totalDurationSeconds.toFloat()
        setProgress(calculateProgress(delay))
        changeProgressColor(R.color.progress_bar_color)
    }

    private fun calculateProgress(delay: SessionViewDelegateState.SecurityDelay): Float {
        return delay.ms / 1000f
    }
}
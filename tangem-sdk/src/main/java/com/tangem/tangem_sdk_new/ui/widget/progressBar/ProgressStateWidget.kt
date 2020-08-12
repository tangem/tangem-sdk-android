package com.tangem.tangem_sdk_new.ui.widget.progressBar

import android.graphics.drawable.AnimatedVectorDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tangem.tangem_sdk_new.R
import com.tangem.tangem_sdk_new.SessionViewDelegateState
import com.tangem.tangem_sdk_new.extensions.show

/**
[REDACTED_AUTHOR]
 */
interface StateWidget<P> {
    fun getView(): View
    fun apply(params: P)
}

class ProgressbarStateWidget(private val mainView: View) : StateWidget<SessionViewDelegateState> {

    init {
        NoneState(mainView)
    }

    override fun getView(): View = mainView

    override fun apply(params: SessionViewDelegateState) {
        val viewState = when (params) {
            is SessionViewDelegateState.SecurityDelay -> SecurityDelayState(mainView)
            is SessionViewDelegateState.Delay -> DelayState(mainView)
            is SessionViewDelegateState.Success -> SuccessState(mainView)
            is SessionViewDelegateState.Error -> ErrorState(mainView)
            is SessionViewDelegateState.WrongCard -> ErrorState(mainView)
            is SessionViewDelegateState.PinRequested -> WarningState(mainView)
            is SessionViewDelegateState.TagConnected -> IndeterminateProgressState(mainView)
            else -> NoneState(mainView)
        }
        viewState.apply(params)
    }
}

abstract class BaseState(private val mainView: View) : StateWidget<SessionViewDelegateState> {
    protected val progressBar: SdkProgressBar = mainView.findViewById(R.id.progressBar)
    protected val tvProgressValue: TextView = mainView.findViewById(R.id.tvProgressValue)

    protected val doneView: ImageView = mainView.findViewById(R.id.imvSuccess)
    protected val exclamationView: ImageView = mainView.findViewById(R.id.imvExclamation)


    init {
        hideViews(tvProgressValue, progressBar, doneView, exclamationView)
    }

    override fun getView(): View = mainView

    protected fun setMaxProgress() {
        setProgress(progressBar.progressMax)
    }

    protected fun setProgress(progress: Float, withAnimation: Boolean = true) {
        if (progressBar.isIndeterminate) progressBar.isIndeterminate = false

        if (withAnimation) progressBar.setProgressWithAnimation(progress)
        else progressBar.progress = progress
    }

    protected fun changeProgressColor(color: Int, withAnimation: Boolean = true) {
        val parsedColor = ContextCompat.getColor(progressBar.context, color)

        if (withAnimation) progressBar.setProgressBarColorWithAnimation(parsedColor)
        else progressBar.progressBarColor = parsedColor

    }

    protected fun changeExclamationColor(color: Int) {
        exclamationView.drawable.setTint(ContextCompat.getColor(progressBar.context, color))
    }

    protected fun showViews(vararg views: View) {
        views.forEach { it.show(true) }
    }

    protected fun hideViews(vararg views: View) {
        views.forEach { it.show(false) }
    }

    protected fun showAndAnimateDrawable(view: ImageView) {
        view.show(true)
        val drawable = view.drawable as? AnimatedVectorDrawable ?: return

        drawable.start()
    }
}

class NoneState(mainView: View) : BaseState(mainView) {
    override fun apply(params: SessionViewDelegateState) {
        hideViews(progressBar, tvProgressValue, doneView, exclamationView)

        changeProgressColor(R.color.progress_bar_secondary_color, false)
        setProgress(0f, false)
    }
}

class SuccessState(mainView: View) : BaseState(mainView) {
    override fun apply(params: SessionViewDelegateState) {
        hideViews(tvProgressValue, progressBar, exclamationView)
        showViews(progressBar)

        changeProgressColor(R.color.progress_bar_state_success_color)
        setMaxProgress()
        showAndAnimateDrawable(doneView)
    }
}


class ErrorState(mainView: View) : BaseState(mainView) {
    override fun apply(params: SessionViewDelegateState) {
        hideViews(tvProgressValue, doneView)
        showViews(progressBar)

        val color = R.color.progress_bar_state_error_color
        changeProgressColor(color)
        setMaxProgress()
        changeExclamationColor(color)
        showAndAnimateDrawable(exclamationView)
    }
}

class WarningState(mainView: View) : BaseState(mainView) {
    override fun apply(params: SessionViewDelegateState) {
        hideViews(tvProgressValue, doneView)
        showViews(progressBar)

        val color = R.color.progress_bar_state_warning_color
        changeProgressColor(color)
        setMaxProgress()
        changeExclamationColor(color)
        showAndAnimateDrawable(exclamationView)
    }
}

class IndeterminateProgressState(mainView: View) : BaseState(mainView) {
    override fun apply(params: SessionViewDelegateState) {
        hideViews(tvProgressValue, doneView, exclamationView)
        showViews(progressBar)

        progressBar.isIndeterminate = true
        changeProgressColor(R.color.progress_bar_color)
    }
}

class SecurityDelayState(mainView: View) : BaseState(mainView) {
    override fun apply(params: SessionViewDelegateState) {
        val delay = params as? SessionViewDelegateState.SecurityDelay ?: return

        hideViews(doneView, exclamationView)
        showViews(progressBar, tvProgressValue)

        if (progressBar.progressMax != params.totalDurationSeconds.toFloat()) {
            progressBar.progressMax = params.totalDurationSeconds.toFloat()
        }
        val progress = delay.totalDurationSeconds - delay.ms + 100
        setProgress(progress.toFloat())
        tvProgressValue.text = params.ms.div(100).toString()

        changeProgressColor(R.color.progress_bar_color)
    }

}

class DelayState(mainView: View) : BaseState(mainView) {
    override fun apply(params: SessionViewDelegateState) {
        val delay = params as? SessionViewDelegateState.Delay ?: return

        hideViews(doneView)
        hideViews(exclamationView)

        showViews(progressBar)
        showViews(tvProgressValue)

        if (delay.current == 0) {
            setProgress(0f, false)
        }
        if (progressBar.progressMax != delay.total.toFloat()) {
            progressBar.progressMax = delay.total.toFloat()
        }
        setProgress(delay.current.toFloat())
        tvProgressValue.text = (((delay.total - delay.current) / delay.step) + 1).toString()

        changeProgressColor(R.color.progress_bar_color)
    }
}
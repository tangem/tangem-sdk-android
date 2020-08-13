package com.tangem.tangem_sdk_new.ui.widget.progressBar

import android.graphics.drawable.AnimatedVectorDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tangem.tangem_sdk_new.R
import com.tangem.tangem_sdk_new.SessionViewDelegateState
import com.tangem.tangem_sdk_new.extensions.show
import com.tangem.tangem_sdk_new.ui.widget.BaseSessionDelegateStateWidget

/**
[REDACTED_AUTHOR]
 */

class ProgressbarStateWidget(mainView: View) : BaseSessionDelegateStateWidget(mainView) {

    init {
        NoneState(mainView)
    }

    override fun setState(params: SessionViewDelegateState) {
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
        viewState.setState(params)
    }

    override fun onBottomSheetDismiss() {
        NoneState(mainView)
    }
}

abstract class BaseProgressState(mainView: View) : BaseSessionDelegateStateWidget(mainView) {
    protected val progressBar: SdkProgressBar = mainView.findViewById(R.id.progressBar)
    protected val tvProgressValue: TextView = mainView.findViewById(R.id.tvProgressValue)

    protected val doneView: ImageView = mainView.findViewById(R.id.imvSuccess)
    protected val exclamationView: ImageView = mainView.findViewById(R.id.imvExclamation)


    init {
        hideViews(tvProgressValue, progressBar, doneView, exclamationView)
    }

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

class NoneState(mainView: View) : BaseProgressState(mainView) {
    override fun setState(params: SessionViewDelegateState) {
        hideViews(progressBar, tvProgressValue, doneView, exclamationView)

        changeProgressColor(R.color.progress_bar_secondary_color, false)
        setProgress(0f, false)
    }
}

class SuccessState(mainView: View) : BaseProgressState(mainView) {
    override fun setState(params: SessionViewDelegateState) {
        hideViews(tvProgressValue, progressBar, exclamationView)
        showViews(progressBar)

        changeProgressColor(R.color.progress_bar_state_success_color)
        setMaxProgress()
        showAndAnimateDrawable(doneView)
    }
}


class ErrorState(mainView: View) : BaseProgressState(mainView) {
    override fun setState(params: SessionViewDelegateState) {
        hideViews(tvProgressValue, doneView)
        showViews(progressBar)

        val color = R.color.progress_bar_state_error_color
        changeProgressColor(color)
        setMaxProgress()
        changeExclamationColor(color)
        showAndAnimateDrawable(exclamationView)
    }
}

class WarningState(mainView: View) : BaseProgressState(mainView) {
    override fun setState(params: SessionViewDelegateState) {
        hideViews(tvProgressValue, doneView)
        showViews(progressBar)

        val color = R.color.progress_bar_state_warning_color
        changeProgressColor(color)
        setMaxProgress()
        changeExclamationColor(color)
        showAndAnimateDrawable(exclamationView)
    }
}

class IndeterminateProgressState(mainView: View) : BaseProgressState(mainView) {
    override fun setState(params: SessionViewDelegateState) {
        changeProgressColor(R.color.progress_bar_color, false)

        hideViews(tvProgressValue, doneView, exclamationView)
        showViews(progressBar)

        progressBar.isIndeterminate = true
    }
}

class SecurityDelayState(mainView: View) : BaseProgressState(mainView) {
    override fun setState(params: SessionViewDelegateState) {
        val delay = params as? SessionViewDelegateState.SecurityDelay ?: return

        changeProgressColor(R.color.progress_bar_color, false)
        hideViews(doneView, exclamationView)
        showViews(progressBar, tvProgressValue)

        if (progressBar.progressMax != params.totalDurationSeconds.toFloat()) {
            progressBar.progressMax = params.totalDurationSeconds.toFloat()
        }
        val progress = delay.totalDurationSeconds - delay.ms
        val text = params.ms.div(100).toString()
        setProgress(progress.toFloat())
        tvProgressValue.text = text
    }

}

class DelayState(mainView: View) : BaseProgressState(mainView) {
    override fun setState(params: SessionViewDelegateState) {
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
        val percent = delay.current * 100 / delay.total
        tvProgressValue.text = "$percent %"

        changeProgressColor(R.color.progress_bar_color)
    }
}
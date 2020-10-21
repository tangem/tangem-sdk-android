package com.tangem.tangem_sdk_new.ui.widget.howTo

import android.content.Context
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextSwitcher
import com.skyfishjy.library.RippleBackground
import com.tangem.tangem_sdk_new.R
import com.tangem.tangem_sdk_new.SessionViewDelegateState
import com.tangem.tangem_sdk_new.extensions.dpToPx
import com.tangem.tangem_sdk_new.nfc.NfcLocationProvider
import com.tangem.tangem_sdk_new.nfc.NfcManager
import com.tangem.tangem_sdk_new.ui.animation.VoidCallback
import com.tangem.tangem_sdk_new.ui.widget.BaseSessionDelegateStateWidget
import com.tangem.tangem_sdk_new.ui.widget.BaseStateWidget

/**
[REDACTED_AUTHOR]
 */
class HowToTapWidget constructor(
    mainView: View,
    private val nfcManager: NfcManager,
    private val nfcLocationProvider: NfcLocationProvider,
) : BaseSessionDelegateStateWidget(mainView), OkCallback {

    var previousState: SessionViewDelegateState? = null

    private val view: View
    private val controller: HowToController

    override var onOk: VoidCallback? = null
        set(value) {
            field = value
            controller.onOk = value
        }

    init {
        val layoutInflater = LayoutInflater.from(mainView.context)
        val nfcLocation = nfcLocationProvider.getLocation()
        val vibrator = mainView.context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        val widget = if (nfcLocation == null) {
            view = layoutInflater.inflate(R.layout.how_to_unknown, null)
            NfcUnknownWidget(view)
        } else {
            view = layoutInflater.inflate(R.layout.how_to_known, null)
            NfcKnownWidget(view, nfcLocation)
        }
        controller = HowToController(widget, vibrator, nfcManager)

    }

    override fun setState(params: SessionViewDelegateState) {
        when (params) {
            SessionViewDelegateState.HowToTap -> {
                nfcManager.readingIsActive = false
                (mainView as? ViewGroup)?.addView(view)
                controller.start()
            }
            else -> {
                controller.stop()
                nfcManager.readingIsActive = true
                (mainView as? ViewGroup)?.removeAllViews()
            }
        }
    }
}


interface OkCallback {
    var onOk: VoidCallback?
}

abstract class NfcHowToWidget(mainView: View) : BaseStateWidget<HowToState>(mainView), OkCallback {

    var onEnd: VoidCallback? = null
    override var onOk: VoidCallback? = null

    protected val context = mainView.context
    protected val rippleView: RippleBackground = mainView.findViewById(R.id.rippleBg)
    protected val tvSwitcher: TextSwitcher = mainView.findViewById(R.id.tvHowToSwitcher)
    protected val phone: ImageView = mainView.findViewById(R.id.imvPhone)
    protected val btnCancel: Button = mainView.findViewById(R.id.btnCancel)

    protected var currentState: HowToState? = null
    protected var isCancelled = false

    init {
        initTextChangesAnimation()
        btnCancel.setOnClickListener { onOk?.invoke() }
    }

    override fun onBottomSheetDismiss() {
        setState(HowToState.Cancel)
    }

    protected fun setText(textId: Int) {
        tvSwitcher.setText(tvSwitcher.context.getString(textId))
    }

    protected fun setMainButtonText(textId: Int) {
        btnCancel.setText(textId)
    }

    protected fun dpToPx(value: Float): Float = mainView.dpToPx(value)

    private fun initTextChangesAnimation() {
        tvSwitcher.setInAnimation(context, android.R.anim.slide_in_left)
        tvSwitcher.setOutAnimation(context, android.R.anim.slide_out_right)
    }
}

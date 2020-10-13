package com.tangem.tangem_sdk_new.howTo

import android.content.Context
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.tangem.tangem_sdk_new.R
import com.tangem.tangem_sdk_new.howTo.known.NfcKnownController
import com.tangem.tangem_sdk_new.howTo.known.NfcKnownWidget
import com.tangem.tangem_sdk_new.howTo.unknown.NfcUnknownController
import com.tangem.tangem_sdk_new.howTo.unknown.NfcUnknownWidget
import com.tangem.tangem_sdk_new.nfc.NfcLocationProvider
import com.tangem.tangem_sdk_new.nfc.NfcManager

/**
[REDACTED_AUTHOR]
 */
class HowToTap constructor(
    private val activity: AppCompatActivity,
    private val nfcManager: NfcManager,
    private val nfcLocationProvider: NfcLocationProvider,
) {

    val view: View

    init {
        val layoutInflater = LayoutInflater.from(activity)
        val nfcLocation = nfcLocationProvider.getLocation()
        view = if (nfcLocation == null) {
            val view = layoutInflater.inflate(R.layout.how_to_unknown, null)
            val vibrator = activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            NfcUnknownController(NfcUnknownWidget(view), vibrator, nfcManager).start()
            view
        } else {
            val view = layoutInflater.inflate(R.layout.how_to_known, null)
            val nfcKnownController = NfcKnownController(NfcKnownWidget(view, nfcLocation))
            nfcKnownController.start()
            nfcKnownController.setOnFinishAnimationListener { nfcKnownController.start() }
            view
        }

    }

    private val sharedPrefs = activity.getSharedPreferences("HowToTap", Context.MODE_PRIVATE)
}
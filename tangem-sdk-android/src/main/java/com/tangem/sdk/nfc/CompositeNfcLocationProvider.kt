package com.tangem.sdk.nfc

import android.nfc.NfcAdapter
import com.tangem.sdk.ui.NfcLocation
import com.tangem.sdk.ui.NfcLocationData

class CompositeNfcLocationProvider(
    private val deviceLocationProvider: NfcLocationProvider,
    nfcAdapter: NfcAdapter,
) {

    private val calculator: NfcAntennaCalculator = NfcAntennaCalculator(nfcAdapter)

    fun getLocationData(): NfcLocationData {
        // Strategy 1: Try to calculate dynamically from Android API
        calculator.calculatePosition()?.let { position ->
            return NfcLocationData.Dynamic(position)
        }

        // Strategy 2: Try device-based provider
        deviceLocationProvider.getLocation()?.let { location ->
            return NfcLocationData.Predefined(location)
        }

        // Strategy 3: Default fallback
        return NfcLocationData.Predefined(NfcLocation.Model13)
    }

    fun getLocation(): NfcLocation? {
        return deviceLocationProvider.getLocation()
    }
}
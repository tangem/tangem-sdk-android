package com.tangem.tangem_sdk_new.ui.widget.howTo

/**
[REDACTED_AUTHOR]
 */
sealed class HowToState {
    sealed class Known : HowToState() {
        object Prepare : Known()
        object ShowNfcPosition : Known()
        object TapToKnownPosition : Known()
    }

    sealed class Unknown : HowToState() {
        object FindAntenna : Unknown()
        object AntennaFound : Unknown()
        object Cancel : Unknown()
    }
}
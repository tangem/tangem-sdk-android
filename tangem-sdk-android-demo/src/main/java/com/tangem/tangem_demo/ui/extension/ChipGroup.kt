package com.tangem.tangem_demo.ui.extension

import androidx.core.view.forEach
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.tangem.tangem_demo.GlobalLayoutStateHandler

/**
[REDACTED_AUTHOR]
 */
fun ChipGroup.fitChipsByGroupWidth() {
    val layoutStateHandler = GlobalLayoutStateHandler(this)
    layoutStateHandler.onStateChanged = stateHandler@{
        if (it.childCount < 2) {
            layoutStateHandler.detach()
            return@stateHandler
        }

        val spacingBetweenViews = it.chipSpacingHorizontal * (it.childCount - 1)
        val width = (it.width - spacingBetweenViews) / it.childCount
        it.forEach { chip -> (chip as? Chip)?.width = width }
        layoutStateHandler.detach()
    }
}
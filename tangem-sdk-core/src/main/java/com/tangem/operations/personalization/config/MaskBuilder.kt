package com.tangem.operations.personalization.config

import com.tangem.common.Mask

internal class MaskBuilder {
    var maskValue = 0
        private set

    fun add(maskCode: Mask.Code) {
        maskValue = maskValue or maskCode.value
    }

    inline fun <reified T : Mask> build(): T = T::class.java.constructors[0].newInstance(maskValue) as T
}
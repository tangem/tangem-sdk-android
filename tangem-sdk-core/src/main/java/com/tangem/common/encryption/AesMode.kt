package com.tangem.common.encryption

enum class AesMode(val p1: Int) {
    None(0),
    CbcFast(0x01),
    CbcStrong(0x02),
    Ccm(0x10)
}
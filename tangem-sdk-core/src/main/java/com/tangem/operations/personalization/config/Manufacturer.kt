package com.tangem.operations.personalization.config

import com.tangem.common.KeyPair

data class Manufacturer(
    val keyPair: KeyPair,
    val name: String? = null,
)
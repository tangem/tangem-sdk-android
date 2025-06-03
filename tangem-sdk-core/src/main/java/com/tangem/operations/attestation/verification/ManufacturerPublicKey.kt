package com.tangem.operations.attestation.verification

/**
 * Manufacturer public keys collection
 *
 * @property id    manufacturer name
 * @property value manufacturer public key
 *
[REDACTED_AUTHOR]
 */
enum class ManufacturerPublicKey(val id: String, val value: String) {

    Tangem(id = "TANGEM", value = "02630EC6371DA464986F51346B64E6A9711C530B1DD5FC3A145414373C231F7862"),

    SmartCash(
        id = "SMART CASH",
        value = "042EDE119BF337B264FDA132CFC7C177824D3617DAC80F25DBB2A4A8A1183C03B9152305F8F1DB97004518480D5091ADC1C" +
            "AB9EACCC18E1B9E9C3BEFB293DD37B2",
    ),

    TangemSDK(id = "TANGEM SDK", value = "03BAB86D56298C996F564A84FC88E28AED38184B12F07E519113BEF48C76F3DF3A"),

    SmartCashSDK(
        id = "SMART CASH SDK",
        value = "03BAB86D56298C996F564A84FC88E28AED38184B12F07E519113BEF48C76F3DF3A",
    ),

    ;
}
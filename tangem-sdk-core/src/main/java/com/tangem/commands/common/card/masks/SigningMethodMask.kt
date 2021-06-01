package com.tangem.commands.common.card.masks

/**
 * Determines which type of data is required for signing.
 */
data class SigningMethodMask(val rawValue: Int) {

    fun contains(signingMethod: SigningMethod): Boolean {
        return if (rawValue and 0x80 == 0) {
            signingMethod.code == rawValue
        } else {
            rawValue and (0x01 shl signingMethod.code) != 0
        }
    }

    override fun toString(): String {
        return SigningMethod.values().filter { contains(it) }.joinToString(", ")
    }

    fun toList(): List<SigningMethod> = SigningMethod.values().filter { contains(it) }.map { it }
}

enum class SigningMethod(val code: Int) {
    SignHash(0),
    SignRaw(1),
    SignHashSignedByIssuer(2),
    SignRawSignedByIssuer(3),
    SignHashSignedByIssuerAndUpdateIssuerData(4),
    SignRawSignedByIssuerAndUpdateIssuerData(5),
    SignPos(6)
}

class SigningMethodMaskBuilder() {

    private val signingMethods = mutableSetOf<SigningMethod>()

    fun add(signingMethod: SigningMethod) {
        signingMethods.add(signingMethod)
    }

    fun build(): SigningMethodMask {
        val rawValue: Int = when {
            signingMethods.count() == 0 -> {
                0
            }
            signingMethods.count() == 1 -> {
                signingMethods.iterator().next().code
            }
            else -> {
                signingMethods.fold(
                    0x80, { acc, singingMethod -> acc + (0x01 shl singingMethod.code) }
                )
            }
        }
        return SigningMethodMask(rawValue)
    }
}
package com.tangem.common

/**
[REDACTED_AUTHOR]
 */
interface Mask {
    val rawValue: Int
    val values: List<Code>

    fun contains(code: Code): Boolean {
        return rawValue and code.value != 0
    }

    interface Code {
        val value: Int
        val name: String
    }
}

abstract class BaseMask : Mask {
    fun toList(): List<Mask.Code> = values.filter { contains(it) }.map { it }

    override fun toString(): String = values.filter { contains(it) }.joinToString(", ")
}
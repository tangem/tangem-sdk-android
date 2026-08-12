package com.tangem.common.card

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SigningMethodTest {

    // region Code enum values

    @Test
    fun codeValues() {
        assertThat(SigningMethod.Code.SignHash.value).isEqualTo(0)
        assertThat(SigningMethod.Code.SignRaw.value).isEqualTo(1)
        assertThat(SigningMethod.Code.SignHashSignedByIssuer.value).isEqualTo(2)
        assertThat(SigningMethod.Code.SignRawSignedByIssuer.value).isEqualTo(3)
        assertThat(SigningMethod.Code.SignHashSignedByIssuerAndUpdateIssuerData.value).isEqualTo(4)
        assertThat(SigningMethod.Code.SignRawSignedByIssuerAndUpdateIssuerData.value).isEqualTo(5)
        assertThat(SigningMethod.Code.SignPos.value).isEqualTo(6)
    }

    @Test
    fun codeCount() {
        assertThat(SigningMethod.Code.values()).hasLength(7)
    }

    // endregion

    // region Single method mode (rawValue bit 7 = 0)

    @Test
    fun singleMethodSignHash() {
        val method = SigningMethod(SigningMethod.Code.SignHash.value)
        assertThat(method.rawValue).isEqualTo(0)
        assertThat(method.contains(SigningMethod.Code.SignHash)).isTrue()
        assertThat(method.contains(SigningMethod.Code.SignRaw)).isFalse()
    }

    @Test
    fun singleMethodSignRaw() {
        val method = SigningMethod(SigningMethod.Code.SignRaw.value)
        assertThat(method.rawValue).isEqualTo(1)
        assertThat(method.contains(SigningMethod.Code.SignRaw)).isTrue()
        assertThat(method.contains(SigningMethod.Code.SignHash)).isFalse()
    }

    @Test
    fun singleMethodSignHashSignedByIssuer() {
        val method = SigningMethod(SigningMethod.Code.SignHashSignedByIssuer.value)
        assertThat(method.contains(SigningMethod.Code.SignHashSignedByIssuer)).isTrue()
        assertThat(method.contains(SigningMethod.Code.SignRaw)).isFalse()
    }

    @Test
    fun singleMethodSignPos() {
        val method = SigningMethod(SigningMethod.Code.SignPos.value)
        assertThat(method.contains(SigningMethod.Code.SignPos)).isTrue()
        assertThat(method.contains(SigningMethod.Code.SignHash)).isFalse()
    }

    @Test
    fun singleMethodDoesNotContainOtherCodes() {
        val method = SigningMethod(SigningMethod.Code.SignRawSignedByIssuer.value)
        SigningMethod.Code.values().forEach { code ->
            if (code == SigningMethod.Code.SignRawSignedByIssuer) {
                assertThat(method.contains(code)).isTrue()
            } else {
                assertThat(method.contains(code)).isFalse()
            }
        }
    }

    // endregion

    // region Multi-method bitmask mode (rawValue bit 7 = 1)

    @Test
    fun multiMethodContainsBoth() {
        // 0x80 + (1 << 0) + (1 << 1) = 0x83
        val method = SigningMethod(0x83)
        assertThat(method.contains(SigningMethod.Code.SignHash)).isTrue()
        assertThat(method.contains(SigningMethod.Code.SignRaw)).isTrue()
        assertThat(method.contains(SigningMethod.Code.SignHashSignedByIssuer)).isFalse()
    }

    @Test
    fun multiMethodContainsSpecificCodes() {
        // 0x80 + (1 << 2) + (1 << 4) = 0x80 + 4 + 16 = 0x94
        val method = SigningMethod(0x94)
        assertThat(method.contains(SigningMethod.Code.SignHashSignedByIssuer)).isTrue()
        assertThat(method.contains(SigningMethod.Code.SignHashSignedByIssuerAndUpdateIssuerData)).isTrue()
        assertThat(method.contains(SigningMethod.Code.SignHash)).isFalse()
        assertThat(method.contains(SigningMethod.Code.SignRaw)).isFalse()
    }

    @Test
    fun multiMethodAllCodesSet() {
        // 0x80 + (1<<0) + (1<<1) + (1<<2) + (1<<3) + (1<<4) + (1<<5) + (1<<6) = 0x80 + 0x7F = 0xFF
        val method = SigningMethod(0xFF)
        SigningMethod.Code.values().forEach { code ->
            assertThat(method.contains(code)).isTrue()
        }
    }

    // endregion

    // region build with vararg

    @Test
    fun buildEmpty() {
        val method = SigningMethod.build()
        assertThat(method.rawValue).isEqualTo(0)
    }

    @Test
    fun buildSingleMethod() {
        val method = SigningMethod.build(SigningMethod.Code.SignHash)
        assertThat(method.rawValue).isEqualTo(0)
        assertThat(method.contains(SigningMethod.Code.SignHash)).isTrue()
    }

    @Test
    fun buildSingleMethodSignRaw() {
        val method = SigningMethod.build(SigningMethod.Code.SignRaw)
        assertThat(method.rawValue).isEqualTo(1)
        assertThat(method.contains(SigningMethod.Code.SignRaw)).isTrue()
    }

    @Test
    fun buildTwoMethods() {
        val method = SigningMethod.build(SigningMethod.Code.SignHash, SigningMethod.Code.SignRaw)
        // 0x80 + (1 << 0) + (1 << 1) = 0x83
        assertThat(method.rawValue).isEqualTo(0x83)
        assertThat(method.contains(SigningMethod.Code.SignHash)).isTrue()
        assertThat(method.contains(SigningMethod.Code.SignRaw)).isTrue()
        assertThat(method.contains(SigningMethod.Code.SignHashSignedByIssuer)).isFalse()
    }

    @Test
    fun buildThreeMethods() {
        val method = SigningMethod.build(
            SigningMethod.Code.SignHash,
            SigningMethod.Code.SignRaw,
            SigningMethod.Code.SignPos,
        )
        // 0x80 + (1 << 0) + (1 << 1) + (1 << 6) = 0x80 + 1 + 2 + 64 = 0xC3
        assertThat(method.rawValue).isEqualTo(0xC3)
        assertThat(method.contains(SigningMethod.Code.SignHash)).isTrue()
        assertThat(method.contains(SigningMethod.Code.SignRaw)).isTrue()
        assertThat(method.contains(SigningMethod.Code.SignPos)).isTrue()
        assertThat(method.contains(SigningMethod.Code.SignHashSignedByIssuer)).isFalse()
    }

    @Test
    fun buildAllMethods() {
        val method = SigningMethod.build(*SigningMethod.Code.values())
        assertThat(method.rawValue).isEqualTo(0xFF)
        SigningMethod.Code.values().forEach { code ->
            assertThat(method.contains(code)).isTrue()
        }
    }

    // endregion

    // region toList

    @Test
    fun toListSingleMethod() {
        val method = SigningMethod.build(SigningMethod.Code.SignRaw)
        val list = method.toList()
        assertThat(list).hasSize(1)
        assertThat(list).containsExactly(SigningMethod.Code.SignRaw)
    }

    @Test
    fun toListMultipleMethods() {
        val method = SigningMethod.build(SigningMethod.Code.SignHash, SigningMethod.Code.SignPos)
        val list = method.toList()
        assertThat(list).hasSize(2)
        assertThat(list).containsExactly(SigningMethod.Code.SignHash, SigningMethod.Code.SignPos)
    }

    @Test
    fun toListEmptyBuildDefaultsToSignHash() {
        // build() with no args → rawValue 0 → single mode → matches SignHash (value 0)
        val method = SigningMethod.build()
        val list = method.toList()
        assertThat(list).containsExactly(SigningMethod.Code.SignHash)
    }

    @Test
    fun toListAllMethods() {
        val method = SigningMethod.build(*SigningMethod.Code.values())
        val list = method.toList()
        assertThat(list).hasSize(7)
    }

    // endregion

    // region toString

    @Test
    fun toStringSingleMethod() {
        val method = SigningMethod.build(SigningMethod.Code.SignRaw)
        assertThat(method.toString()).isEqualTo("SignRaw")
    }

    @Test
    fun toStringMultipleMethods() {
        val method = SigningMethod.build(SigningMethod.Code.SignHash, SigningMethod.Code.SignRaw)
        assertThat(method.toString()).isEqualTo("SignHash, SignRaw")
    }

    @Test
    fun toStringEmptyBuildDefaultsToSignHash() {
        // build() with no args → rawValue 0 → single mode → matches SignHash
        val method = SigningMethod.build()
        assertThat(method.toString()).isEqualTo("SignHash")
    }

    // endregion

    // region rawValue

    @Test
    fun rawValuePreserved() {
        val method = SigningMethod(42)
        assertThat(method.rawValue).isEqualTo(42)
    }

    @Test
    fun rawValueZero() {
        val method = SigningMethod(0)
        assertThat(method.rawValue).isEqualTo(0)
    }

    // endregion

    // region Edge cases: bit 7 boundary

    @Test
    fun rawValue127IsSingleMode() {
        // 0x7F (127) — bit 7 is 0, single mode
        // No Code has value 127, so nothing matches
        val method = SigningMethod(0x7F)
        SigningMethod.Code.values().forEach { code ->
            assertThat(method.contains(code)).isFalse()
        }
    }

    @Test
    fun rawValue128IsMultiMode() {
        // 0x80 (128) — bit 7 is 1, multi mode, but no code bits set
        val method = SigningMethod(0x80)
        SigningMethod.Code.values().forEach { code ->
            assertThat(method.contains(code)).isFalse()
        }
    }

    @Test
    fun rawValue129IsMultiModeWithSignHash() {
        // 0x81 = 0x80 + (1 << 0) → SignHash
        val method = SigningMethod(0x81)
        assertThat(method.contains(SigningMethod.Code.SignHash)).isTrue()
        assertThat(method.contains(SigningMethod.Code.SignRaw)).isFalse()
    }

    // endregion
}
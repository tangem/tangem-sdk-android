package com.tangem.crypto.hdWallet

import com.google.common.truth.Truth.assertThat
import com.tangem.common.extensions.toByteArray
import com.tangem.crypto.hdWallet.DerivationNode.Companion.deserialize
import com.tangem.crypto.hdWallet.DerivationNode.Companion.serialize
import com.tangem.crypto.hdWallet.bip32.BIP32
import org.junit.Test

class DerivationNodeTest {

    private val hardenedOffset = BIP32.Constants.hardenedOffset // 2147483648 (0x80000000)

    // region Hardened node

    @Test
    fun hardenedNodeIsHardened() {
        val node = DerivationNode.Hardened(44)
        assertThat(node.isHardened).isTrue()
    }

    @Test
    fun hardenedNodeIndexIncludesOffset() {
        val node = DerivationNode.Hardened(44)
        assertThat(node.index).isEqualTo(44 + hardenedOffset)
    }

    @Test
    fun hardenedNodeGetIndexWithoutOffset() {
        val node = DerivationNode.Hardened(44)
        assertThat(node.getIndex(includeHardened = false)).isEqualTo(44)
    }

    @Test
    fun hardenedNodeGetIndexWithOffset() {
        val node = DerivationNode.Hardened(44)
        assertThat(node.getIndex(includeHardened = true)).isEqualTo(44 + hardenedOffset)
    }

    @Test
    fun hardenedNodePathDescription() {
        val node = DerivationNode.Hardened(44)
        assertThat(node.pathDescription).isEqualTo("44'")
    }

    @Test
    fun hardenedNodeZeroIndex() {
        val node = DerivationNode.Hardened(0)
        assertThat(node.index).isEqualTo(hardenedOffset)
        assertThat(node.pathDescription).isEqualTo("0'")
    }

    // endregion

    // region NonHardened node

    @Test
    fun nonHardenedNodeIsNotHardened() {
        val node = DerivationNode.NonHardened(0)
        assertThat(node.isHardened).isFalse()
    }

    @Test
    fun nonHardenedNodeIndexHasNoOffset() {
        val node = DerivationNode.NonHardened(42)
        assertThat(node.index).isEqualTo(42)
    }

    @Test
    fun nonHardenedNodeGetIndexIgnoresFlag() {
        val node = DerivationNode.NonHardened(42)
        assertThat(node.getIndex(includeHardened = true)).isEqualTo(42)
        assertThat(node.getIndex(includeHardened = false)).isEqualTo(42)
    }

    @Test
    fun nonHardenedNodePathDescription() {
        val node = DerivationNode.NonHardened(1)
        assertThat(node.pathDescription).isEqualTo("1")
    }

    @Test
    fun nonHardenedNodeZeroIndex() {
        val node = DerivationNode.NonHardened(0)
        assertThat(node.index).isEqualTo(0)
        assertThat(node.pathDescription).isEqualTo("0")
    }

    // endregion

    // region fromIndex

    @Test
    fun fromIndexBelowOffsetCreatesNonHardened() {
        val node = DerivationNode.fromIndex(42)
        assertThat(node).isInstanceOf(DerivationNode.NonHardened::class.java)
        assertThat(node.isHardened).isFalse()
        assertThat(node.index).isEqualTo(42)
    }

    @Test
    fun fromIndexZeroCreatesNonHardened() {
        val node = DerivationNode.fromIndex(0)
        assertThat(node).isInstanceOf(DerivationNode.NonHardened::class.java)
        assertThat(node.index).isEqualTo(0)
    }

    @Test
    fun fromIndexAtOffsetCreatesHardened() {
        val node = DerivationNode.fromIndex(hardenedOffset)
        assertThat(node).isInstanceOf(DerivationNode.Hardened::class.java)
        assertThat(node.isHardened).isTrue()
        // Hardened(0) → index = 0 + offset = offset
        assertThat(node.index).isEqualTo(hardenedOffset)
    }

    @Test
    fun fromIndexAboveOffsetCreatesHardened() {
        val node = DerivationNode.fromIndex(hardenedOffset + 44)
        assertThat(node).isInstanceOf(DerivationNode.Hardened::class.java)
        assertThat(node.index).isEqualTo(hardenedOffset + 44)
        assertThat(node.getIndex(includeHardened = false)).isEqualTo(44)
    }

    @Test
    fun fromIndexJustBelowOffsetCreatesNonHardened() {
        val node = DerivationNode.fromIndex(hardenedOffset - 1)
        assertThat(node).isInstanceOf(DerivationNode.NonHardened::class.java)
        assertThat(node.index).isEqualTo(hardenedOffset - 1)
    }

    // endregion

    // region serialize / deserialize

    @Test
    fun serializeHardenedNode() {
        val node = DerivationNode.Hardened(44)
        val bytes = node.serialize()
        assertThat(bytes).hasLength(4)
        // index = 44 + 0x80000000 = 0x8000002C
        val expected = (44L + hardenedOffset).toByteArray(size = 4)
        assertThat(bytes).isEqualTo(expected)
    }

    @Test
    fun serializeNonHardenedNode() {
        val node = DerivationNode.NonHardened(1)
        val bytes = node.serialize()
        assertThat(bytes).hasLength(4)
        val expected = 1L.toByteArray(size = 4)
        assertThat(bytes).isEqualTo(expected)
    }

    @Test
    fun deserializeHardenedNode() {
        val bytes = (44L + hardenedOffset).toByteArray(size = 4)
        val node = deserialize(bytes)
        assertThat(node).isInstanceOf(DerivationNode.Hardened::class.java)
        assertThat(node.isHardened).isTrue()
        assertThat(node.getIndex(includeHardened = false)).isEqualTo(44)
    }

    @Test
    fun deserializeNonHardenedNode() {
        val bytes = 1L.toByteArray(size = 4)
        val node = deserialize(bytes)
        assertThat(node).isInstanceOf(DerivationNode.NonHardened::class.java)
        assertThat(node.isHardened).isFalse()
        assertThat(node.index).isEqualTo(1)
    }

    @Test
    fun serializeDeserializeRoundtripHardened() {
        val original = DerivationNode.Hardened(44)
        val restored = deserialize(original.serialize())
        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun serializeDeserializeRoundtripNonHardened() {
        val original = DerivationNode.NonHardened(0)
        val restored = deserialize(original.serialize())
        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun serializeDeserializeRoundtripViaFromIndex() {
        // fromIndex(hardenedOffset + 44) → Hardened(44)
        val original = DerivationNode.fromIndex(hardenedOffset + 44)
        val restored = deserialize(original.serialize())
        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun deserializeZero() {
        val bytes = 0L.toByteArray(size = 4)
        val node = deserialize(bytes)
        assertThat(node).isInstanceOf(DerivationNode.NonHardened::class.java)
        assertThat(node.index).isEqualTo(0)
    }

    @Test
    fun deserializeExactOffset() {
        val bytes = hardenedOffset.toByteArray(size = 4)
        val node = deserialize(bytes)
        assertThat(node).isInstanceOf(DerivationNode.Hardened::class.java)
        assertThat(node.getIndex(includeHardened = false)).isEqualTo(0)
    }

    // endregion

    // region equals / hashCode

    @Test
    fun equalHardenedNodes() {
        val a = DerivationNode.Hardened(44)
        val b = DerivationNode.Hardened(44)
        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @Test
    fun equalNonHardenedNodes() {
        val a = DerivationNode.NonHardened(0)
        val b = DerivationNode.NonHardened(0)
        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @Test
    fun differentIndicesNotEqual() {
        val a = DerivationNode.Hardened(44)
        val b = DerivationNode.Hardened(60)
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun hardenedAndNonHardenedSameIndexNotEqual() {
        val a = DerivationNode.Hardened(0)
        val b = DerivationNode.NonHardened(0)
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun notEqualToNull() {
        val node = DerivationNode.Hardened(0)
        assertThat(node.equals(null)).isFalse()
    }

    @Test
    fun notEqualToOtherType() {
        val node = DerivationNode.Hardened(0)
        assertThat(node.equals("not a node")).isFalse()
    }

    @Test
    fun fromIndexEqualsDirectConstruction() {
        val fromIdx = DerivationNode.fromIndex(hardenedOffset + 44)
        val direct = DerivationNode.Hardened(44)
        assertThat(fromIdx).isEqualTo(direct)
    }

    // endregion

    // region BIP44 path components

    @Test
    fun typicalBip44Nodes() {
        // m/44'/0'/0'/0/0
        val purpose = DerivationNode.Hardened(44)
        val coinType = DerivationNode.Hardened(0)
        val account = DerivationNode.Hardened(0)
        val change = DerivationNode.NonHardened(0)
        val addressIndex = DerivationNode.NonHardened(0)

        assertThat(purpose.pathDescription).isEqualTo("44'")
        assertThat(coinType.pathDescription).isEqualTo("0'")
        assertThat(account.pathDescription).isEqualTo("0'")
        assertThat(change.pathDescription).isEqualTo("0")
        assertThat(addressIndex.pathDescription).isEqualTo("0")

        assertThat(purpose.index).isEqualTo(hardenedOffset + 44)
        assertThat(change.index).isEqualTo(0)
    }

    // endregion
}
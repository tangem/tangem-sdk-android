package com.tangem.crypto.hdWallet

import com.google.common.truth.Truth.assertThat
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.toByteArray
import com.tangem.crypto.hdWallet.bip32.BIP32
import org.junit.Test

class DerivationPathTest {

    private val hardenedOffset = BIP32.Constants.hardenedOffset

    // region Constructor from rawPath string

    @Test(expected = HDWalletError.WrongPath::class)
    fun parseMasterOnlyStringThrows() {
        // "m" without any nodes requires the default constructor, not string parsing
        DerivationPath("m")
    }

    @Test
    fun parseSimplePath() {
        val path = DerivationPath("m/44'/0'/0'/0/0")
        assertThat(path.nodes).hasSize(5)
        assertThat(path.nodes[0]).isEqualTo(DerivationNode.Hardened(44))
        assertThat(path.nodes[1]).isEqualTo(DerivationNode.Hardened(0))
        assertThat(path.nodes[2]).isEqualTo(DerivationNode.Hardened(0))
        assertThat(path.nodes[3]).isEqualTo(DerivationNode.NonHardened(0))
        assertThat(path.nodes[4]).isEqualTo(DerivationNode.NonHardened(0))
    }

    @Test
    fun parsePathWithAlternativeHardenedSymbol() {
        val path = DerivationPath("m/44\u2019/0\u2019/0\u2019")
        assertThat(path.nodes).hasSize(3)
        assertThat(path.nodes[0]).isEqualTo(DerivationNode.Hardened(44))
        assertThat(path.nodes[1]).isEqualTo(DerivationNode.Hardened(0))
        assertThat(path.nodes[2]).isEqualTo(DerivationNode.Hardened(0))
    }

    @Test
    fun parseAllNonHardenedPath() {
        val path = DerivationPath("m/1/2/3")
        assertThat(path.nodes).hasSize(3)
        path.nodes.forEach { assertThat(it.isHardened).isFalse() }
        assertThat(path.nodes[0]).isEqualTo(DerivationNode.NonHardened(1))
        assertThat(path.nodes[1]).isEqualTo(DerivationNode.NonHardened(2))
        assertThat(path.nodes[2]).isEqualTo(DerivationNode.NonHardened(3))
    }

    @Test
    fun parseAllHardenedPath() {
        val path = DerivationPath("m/44'/60'/0'")
        assertThat(path.nodes).hasSize(3)
        path.nodes.forEach { assertThat(it.isHardened).isTrue() }
    }

    @Test
    fun parseSingleNodePath() {
        val path = DerivationPath("m/44")
        assertThat(path.nodes).hasSize(1)
        assertThat(path.nodes[0]).isEqualTo(DerivationNode.NonHardened(44))
    }

    @Test
    fun parseSingleHardenedNodePath() {
        val path = DerivationPath("m/44'")
        assertThat(path.nodes).hasSize(1)
        assertThat(path.nodes[0]).isEqualTo(DerivationNode.Hardened(44))
    }

    @Test
    fun parseCaseInsensitive() {
        val path = DerivationPath("M/44'/0'")
        assertThat(path.nodes).hasSize(2)
        assertThat(path.nodes[0]).isEqualTo(DerivationNode.Hardened(44))
    }

    // endregion

    // region Constructor from rawPath - error cases

    @Test(expected = HDWalletError.WrongPath::class)
    fun parseEmptyStringThrows() {
        DerivationPath("")
    }

    @Test(expected = HDWalletError.WrongPath::class)
    fun parseMissingMasterPrefixThrows() {
        DerivationPath("44'/0'/0'")
    }

    @Test(expected = HDWalletError.WrongPath::class)
    fun parseInvalidCharacterInIndexThrows() {
        DerivationPath("m/abc")
    }

    @Test(expected = HDWalletError.WrongPath::class)
    fun parseNoSeparatorThrows() {
        DerivationPath("x")
    }

    // endregion

    // region Constructor from node list

    @Test
    fun constructFromNodeListEmpty() {
        val path = DerivationPath(listOf())
        assertThat(path.rawPath).isEqualTo("m")
        assertThat(path.nodes).isEmpty()
    }

    @Test
    fun constructFromNodeList() {
        val nodes = listOf(
            DerivationNode.Hardened(44),
            DerivationNode.Hardened(0),
            DerivationNode.NonHardened(0),
        )
        val path = DerivationPath(nodes)
        assertThat(path.rawPath).isEqualTo("m/44'/0'/0")
        assertThat(path.nodes).isEqualTo(nodes)
    }

    @Test
    fun constructFromSingleNode() {
        val path = DerivationPath(listOf(DerivationNode.NonHardened(5)))
        assertThat(path.rawPath).isEqualTo("m/5")
    }

    // endregion

    // region Default constructor (master node)

    @Test
    fun defaultConstructorIsMasterNode() {
        val path = DerivationPath()
        assertThat(path.rawPath).isEqualTo("m")
        assertThat(path.nodes).isEmpty()
    }

    // endregion

    // region rawPath generation

    @Test
    fun rawPathMatchesParsedPath() {
        val original = "m/44'/0'/0'/0/0"
        val path = DerivationPath(original)
        assertThat(path.rawPath).isEqualTo(original)
    }

    @Test
    fun rawPathFromNodesMatchesStringParsed() {
        val fromString = DerivationPath("m/44'/60'/0'")
        val fromNodes = DerivationPath(
            listOf(
                DerivationNode.Hardened(44),
                DerivationNode.Hardened(60),
                DerivationNode.Hardened(0),
            ),
        )
        assertThat(fromNodes.rawPath).isEqualTo(fromString.rawPath)
    }

    // endregion

    // region extendedPath

    @Test
    fun extendedPathAddsNode() {
        val base = DerivationPath("m/44'/0'")
        val extended = base.extendedPath(DerivationNode.NonHardened(0))
        assertThat(extended.nodes).hasSize(3)
        assertThat(extended.nodes[2]).isEqualTo(DerivationNode.NonHardened(0))
        assertThat(extended.rawPath).isEqualTo("m/44'/0'/0")
    }

    @Test
    fun extendedPathDoesNotModifyOriginal() {
        val base = DerivationPath("m/44'")
        base.extendedPath(DerivationNode.Hardened(0))
        assertThat(base.nodes).hasSize(1)
    }

    @Test
    fun extendedPathFromMaster() {
        val master = DerivationPath()
        val extended = master.extendedPath(DerivationNode.Hardened(44))
        assertThat(extended.nodes).hasSize(1)
        assertThat(extended.rawPath).isEqualTo("m/44'")
    }

    @Test
    fun extendedPathChaining() {
        val path = DerivationPath()
            .extendedPath(DerivationNode.Hardened(44))
            .extendedPath(DerivationNode.Hardened(0))
            .extendedPath(DerivationNode.NonHardened(0))
        assertThat(path.nodes).hasSize(3)
        assertThat(path.rawPath).isEqualTo("m/44'/0'/0")
    }

    // endregion

    // region equals / hashCode

    @Test
    fun equalPathsFromSameString() {
        val a = DerivationPath("m/44'/0'/0'")
        val b = DerivationPath("m/44'/0'/0'")
        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @Test
    fun equalPathsFromStringAndNodes() {
        val a = DerivationPath("m/44'/0'")
        val b = DerivationPath(listOf(DerivationNode.Hardened(44), DerivationNode.Hardened(0)))
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun differentPathsNotEqual() {
        val a = DerivationPath("m/44'/0'")
        val b = DerivationPath("m/44'/1'")
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun differentLengthPathsNotEqual() {
        val a = DerivationPath("m/44'")
        val b = DerivationPath("m/44'/0'")
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun notEqualToNull() {
        val path = DerivationPath("m/44'")
        assertThat(path.equals(null)).isFalse()
    }

    @Test
    fun notEqualToOtherType() {
        val path = DerivationPath("m/44'")
        assertThat(path.equals("m/44'")).isFalse()
    }

    @Test
    fun emptyPathsEqual() {
        assertThat(DerivationPath()).isEqualTo(DerivationPath(listOf()))
    }

    // endregion

    // region from(ByteArray)

    @Test
    fun fromByteArraySingleHardenedNode() {
        val bytes = (44L + hardenedOffset).toByteArray(size = 4)
        val path = DerivationPath.from(bytes)
        assertThat(path.nodes).hasSize(1)
        assertThat(path.nodes[0]).isEqualTo(DerivationNode.Hardened(44))
    }

    @Test
    fun fromByteArraySingleNonHardenedNode() {
        val bytes = 1L.toByteArray(size = 4)
        val path = DerivationPath.from(bytes)
        assertThat(path.nodes).hasSize(1)
        assertThat(path.nodes[0]).isEqualTo(DerivationNode.NonHardened(1))
    }

    @Test
    fun fromByteArrayMultipleNodes() {
        val bytes = (44L + hardenedOffset).toByteArray(size = 4) +
            (0L + hardenedOffset).toByteArray(size = 4) +
            0L.toByteArray(size = 4)
        val path = DerivationPath.from(bytes)
        assertThat(path.nodes).hasSize(3)
        assertThat(path.nodes[0]).isEqualTo(DerivationNode.Hardened(44))
        assertThat(path.nodes[1]).isEqualTo(DerivationNode.Hardened(0))
        assertThat(path.nodes[2]).isEqualTo(DerivationNode.NonHardened(0))
    }

    @Test
    fun fromByteArrayEmpty() {
        val path = DerivationPath.from(ByteArray(0))
        assertThat(path.nodes).isEmpty()
    }

    @Test(expected = TangemSdkError.DecodingFailed::class)
    fun fromByteArrayInvalidLengthThrows() {
        DerivationPath.from(ByteArray(3))
    }

    @Test(expected = TangemSdkError.DecodingFailed::class)
    fun fromByteArrayNonMultipleOf4Throws() {
        DerivationPath.from(ByteArray(5))
    }

    // endregion

    // region Roundtrip: string → nodes → string

    @Test
    fun roundtripBip44Path() {
        val original = "m/44'/60'/0'/0/0"
        val path = DerivationPath(original)
        val reconstructed = DerivationPath(path.nodes)
        assertThat(reconstructed.rawPath).isEqualTo(original)
        assertThat(reconstructed).isEqualTo(path)
    }

    @Test
    fun roundtripMasterOnly() {
        val path = DerivationPath()
        val reconstructed = DerivationPath(path.nodes)
        assertThat(reconstructed).isEqualTo(path)
        assertThat(reconstructed.rawPath).isEqualTo("m")
    }

    // endregion

    // region Common blockchain paths

    @Test
    fun bitcoinBip44Path() {
        val path = DerivationPath("m/44'/0'/0'/0/0")
        assertThat(path.nodes).hasSize(5)
        assertThat(path.nodes[0]).isEqualTo(DerivationNode.Hardened(44))
        assertThat(path.nodes[1]).isEqualTo(DerivationNode.Hardened(0))
    }

    @Test
    fun ethereumBip44Path() {
        val path = DerivationPath("m/44'/60'/0'/0/0")
        assertThat(path.nodes[1]).isEqualTo(DerivationNode.Hardened(60))
    }

    // endregion
}
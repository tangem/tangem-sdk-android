package com.tangem.operations

import com.google.common.truth.Truth
import com.tangem.common.json.MoshiJsonConverter
import org.junit.Test

class PreflightReadModeTest {

    @Test
    fun `FullCardRead toString includes options`() {
        val mode: PreflightReadMode = PreflightReadMode.FullCardRead(
            options = setOf(PreflightReadMode.FullCardRead.Option.ReadMasterSecret),
        )

        Truth.assertThat(mode.toString()).contains("ReadMasterSecret")
    }

    @Test
    fun `object modes toString returns simple name`() {
        Truth.assertThat(PreflightReadMode.None.toString()).isEqualTo("None")
        Truth.assertThat(PreflightReadMode.ReadCardOnly.toString()).isEqualTo("ReadCardOnly")
    }

    @Test
    fun `json decoding supports legacy fullCardReadWithAccessCodeCheck value`() {
        val converter = MoshiJsonConverter.INSTANCE

        val legacyMode = converter.fromJson<PreflightReadMode>("\"fullCardReadWithAccessCodeCheck\"")

        Truth.assertThat(legacyMode).isEqualTo(
            PreflightReadMode.FullCardRead(
                options = setOf(
                    PreflightReadMode.FullCardRead.Option.AccessCodeCheck,
                    PreflightReadMode.FullCardRead.Option.ReadMasterSecret,
                    PreflightReadMode.FullCardRead.Option.VerifyBackup,
                ),
            ),
        )
    }

    @Test
    fun `json decoding of current values`() {
        val converter = MoshiJsonConverter.INSTANCE

        mapOf(
            "fullCardRead" to PreflightReadMode.FullCardRead(),
            "readCardOnly" to PreflightReadMode.ReadCardOnly,
            "none" to PreflightReadMode.None,
        ).forEach { (json, mode) ->
            Truth.assertThat(converter.fromJson<PreflightReadMode>("\"$json\"")).isEqualTo(mode)
        }
    }
}
package com.tangem.common.apdu

import com.google.common.truth.Truth.assertThat
import com.tangem.common.core.TangemSdkError
import org.junit.Test

class StatusWordTest {

    // region Enum codes

    @Test
    fun processCompletedCode() {
        assertThat(StatusWord.ProcessCompleted.code).isEqualTo(0x9000)
    }

    @Test
    fun invalidParamsCode() {
        assertThat(StatusWord.InvalidParams.code).isEqualTo(0x6A86)
    }

    @Test
    fun errorProcessingCommandCode() {
        assertThat(StatusWord.ErrorProcessingCommand.code).isEqualTo(0x6286)
    }

    @Test
    fun invalidStateCode() {
        assertThat(StatusWord.InvalidState.code).isEqualTo(0x6985)
    }

    @Test
    fun pinChangedCodes() {
        assertThat(StatusWord.Pin1Changed.code).isEqualTo(0x9001)
        assertThat(StatusWord.Pin2Changed.code).isEqualTo(0x9002)
        assertThat(StatusWord.Pins12Changed.code).isEqualTo(0x9003)
        assertThat(StatusWord.Pin3Changed.code).isEqualTo(0x9004)
        assertThat(StatusWord.Pins13Changed.code).isEqualTo(0x9005)
        assertThat(StatusWord.Pins23Changed.code).isEqualTo(0x9006)
        assertThat(StatusWord.Pins123Changed.code).isEqualTo(0x9007)
    }

    @Test
    fun insNotSupportedCode() {
        assertThat(StatusWord.InsNotSupported.code).isEqualTo(0x6D00)
    }

    @Test
    fun needEncryptionCode() {
        assertThat(StatusWord.NeedEncryption.code).isEqualTo(0x6982)
    }

    @Test
    fun needPauseCode() {
        assertThat(StatusWord.NeedPause.code).isEqualTo(0x9789)
    }

    @Test
    fun fileNotFoundCode() {
        assertThat(StatusWord.FileNotFound.code).isEqualTo(0x6A82)
    }

    @Test
    fun walletNotFoundCode() {
        assertThat(StatusWord.WalletNotFound.code).isEqualTo(0x6A88)
    }

    @Test
    fun invalidAccessCodeCode() {
        assertThat(StatusWord.InvalidAccessCode.code).isEqualTo(0x6AF1)
    }

    @Test
    fun invalidPasscodeCode() {
        assertThat(StatusWord.InvalidPasscode.code).isEqualTo(0x6AF2)
    }

    @Test
    fun walletAlreadyExistsCode() {
        assertThat(StatusWord.WalletAlreadyExists.code).isEqualTo(0x6A89)
    }

    @Test
    fun needResetCode() {
        assertThat(StatusWord.NeedReset.code).isEqualTo(0x6983)
    }

    @Test
    fun accessDeniedCode() {
        assertThat(StatusWord.AccessDenied.code).isEqualTo(0x6AF3)
    }

    @Test
    fun unknownCode() {
        assertThat(StatusWord.Unknown.code).isEqualTo(0x0000)
    }

    // endregion

    // region byCode

    @Test
    fun byCodeProcessCompleted() {
        assertThat(StatusWord.byCode(0x9000)).isEqualTo(StatusWord.ProcessCompleted)
    }

    @Test
    fun byCodeInvalidParams() {
        assertThat(StatusWord.byCode(0x6A86)).isEqualTo(StatusWord.InvalidParams)
    }

    @Test
    fun byCodeErrorProcessingCommand() {
        assertThat(StatusWord.byCode(0x6286)).isEqualTo(StatusWord.ErrorProcessingCommand)
    }

    @Test
    fun byCodeInvalidState() {
        assertThat(StatusWord.byCode(0x6985)).isEqualTo(StatusWord.InvalidState)
    }

    @Test
    fun byCodePinChanged() {
        assertThat(StatusWord.byCode(0x9001)).isEqualTo(StatusWord.Pin1Changed)
        assertThat(StatusWord.byCode(0x9002)).isEqualTo(StatusWord.Pin2Changed)
        assertThat(StatusWord.byCode(0x9003)).isEqualTo(StatusWord.Pins12Changed)
        assertThat(StatusWord.byCode(0x9004)).isEqualTo(StatusWord.Pin3Changed)
        assertThat(StatusWord.byCode(0x9005)).isEqualTo(StatusWord.Pins13Changed)
        assertThat(StatusWord.byCode(0x9006)).isEqualTo(StatusWord.Pins23Changed)
        assertThat(StatusWord.byCode(0x9007)).isEqualTo(StatusWord.Pins123Changed)
    }

    @Test
    fun byCodeInsNotSupported() {
        assertThat(StatusWord.byCode(0x6D00)).isEqualTo(StatusWord.InsNotSupported)
    }

    @Test
    fun byCodeNeedEncryption() {
        assertThat(StatusWord.byCode(0x6982)).isEqualTo(StatusWord.NeedEncryption)
    }

    @Test
    fun byCodeNeedPause() {
        assertThat(StatusWord.byCode(0x9789)).isEqualTo(StatusWord.NeedPause)
    }

    @Test
    fun byCodeFileNotFound() {
        assertThat(StatusWord.byCode(0x6A82)).isEqualTo(StatusWord.FileNotFound)
    }

    @Test
    fun byCodeWalletNotFound() {
        assertThat(StatusWord.byCode(0x6A88)).isEqualTo(StatusWord.WalletNotFound)
    }

    @Test
    fun byCodeInvalidAccessCode() {
        assertThat(StatusWord.byCode(0x6AF1)).isEqualTo(StatusWord.InvalidAccessCode)
    }

    @Test
    fun byCodeInvalidPasscode() {
        assertThat(StatusWord.byCode(0x6AF2)).isEqualTo(StatusWord.InvalidPasscode)
    }

    @Test
    fun byCodeWalletAlreadyExists() {
        assertThat(StatusWord.byCode(0x6A89)).isEqualTo(StatusWord.WalletAlreadyExists)
    }

    @Test
    fun byCodeNeedReset() {
        assertThat(StatusWord.byCode(0x6983)).isEqualTo(StatusWord.NeedReset)
    }

    @Test
    fun byCodeAccessDenied() {
        assertThat(StatusWord.byCode(0x6AF3)).isEqualTo(StatusWord.AccessDenied)
    }

    @Test
    fun byCodeUnknownReturnsUnknown() {
        assertThat(StatusWord.byCode(0x0000)).isEqualTo(StatusWord.Unknown)
    }

    @Test
    fun byCodeUnrecognizedReturnsUnknown() {
        assertThat(StatusWord.byCode(0x1234)).isEqualTo(StatusWord.Unknown)
        assertThat(StatusWord.byCode(0xFFFF)).isEqualTo(StatusWord.Unknown)
        assertThat(StatusWord.byCode(-1)).isEqualTo(StatusWord.Unknown)
    }

    // endregion

    // region toTangemSdkError - success statuses return null

    @Test
    fun processCompletedReturnsNull() {
        assertThat(StatusWord.ProcessCompleted.toTangemSdkError()).isNull()
    }

    @Test
    fun pinChangedStatusesReturnNull() {
        assertThat(StatusWord.Pin1Changed.toTangemSdkError()).isNull()
        assertThat(StatusWord.Pin2Changed.toTangemSdkError()).isNull()
        assertThat(StatusWord.Pins12Changed.toTangemSdkError()).isNull()
        assertThat(StatusWord.Pin3Changed.toTangemSdkError()).isNull()
        assertThat(StatusWord.Pins13Changed.toTangemSdkError()).isNull()
        assertThat(StatusWord.Pins23Changed.toTangemSdkError()).isNull()
        assertThat(StatusWord.Pins123Changed.toTangemSdkError()).isNull()
    }

    @Test
    fun needPauseReturnsNull() {
        assertThat(StatusWord.NeedPause.toTangemSdkError()).isNull()
    }

    @Test
    fun unknownReturnsNull() {
        assertThat(StatusWord.Unknown.toTangemSdkError()).isNull()
    }

    // endregion

    // region toTangemSdkError - error statuses

    @Test
    fun invalidParamsToError() {
        assertThat(StatusWord.InvalidParams.toTangemSdkError())
            .isInstanceOf(TangemSdkError.InvalidParams::class.java)
    }

    @Test
    fun errorProcessingCommandToError() {
        assertThat(StatusWord.ErrorProcessingCommand.toTangemSdkError())
            .isInstanceOf(TangemSdkError.ErrorProcessingCommand::class.java)
    }

    @Test
    fun invalidStateToError() {
        assertThat(StatusWord.InvalidState.toTangemSdkError())
            .isInstanceOf(TangemSdkError.InvalidState::class.java)
    }

    @Test
    fun insNotSupportedToError() {
        assertThat(StatusWord.InsNotSupported.toTangemSdkError())
            .isInstanceOf(TangemSdkError.InsNotSupported::class.java)
    }

    @Test
    fun needEncryptionToError() {
        assertThat(StatusWord.NeedEncryption.toTangemSdkError())
            .isInstanceOf(TangemSdkError.NeedEncryption::class.java)
    }

    @Test
    fun fileNotFoundToError() {
        assertThat(StatusWord.FileNotFound.toTangemSdkError())
            .isInstanceOf(TangemSdkError.FileNotFound::class.java)
    }

    @Test
    fun walletNotFoundToError() {
        assertThat(StatusWord.WalletNotFound.toTangemSdkError())
            .isInstanceOf(TangemSdkError.WalletNotFound::class.java)
    }

    @Test
    fun invalidAccessCodeToError() {
        assertThat(StatusWord.InvalidAccessCode.toTangemSdkError())
            .isInstanceOf(TangemSdkError.AccessCodeRequired::class.java)
    }

    @Test
    fun invalidPasscodeToError() {
        assertThat(StatusWord.InvalidPasscode.toTangemSdkError())
            .isInstanceOf(TangemSdkError.PasscodeRequired::class.java)
    }

    @Test
    fun walletAlreadyExistsToError() {
        assertThat(StatusWord.WalletAlreadyExists.toTangemSdkError())
            .isInstanceOf(TangemSdkError.WalletAlreadyCreated::class.java)
    }

    @Test
    fun needResetToError() {
        assertThat(StatusWord.NeedReset.toTangemSdkError())
            .isInstanceOf(TangemSdkError.NeedReset::class.java)
    }

    @Test
    fun accessDeniedToError() {
        assertThat(StatusWord.AccessDenied.toTangemSdkError())
            .isInstanceOf(TangemSdkError.AccessDenied::class.java)
    }

    // endregion

    // region All enum values have unique codes

    @Test
    fun allCodesUnique() {
        val values = StatusWord.values()
        val codes = values.map { it.code }
        assertThat(codes).containsNoDuplicates()
    }

    // endregion

    // region byCode roundtrip for all values

    @Test
    fun byCodeRoundtripForAllValues() {
        StatusWord.values().forEach { statusWord ->
            assertThat(StatusWord.byCode(statusWord.code)).isEqualTo(statusWord)
        }
    }

    // endregion
}
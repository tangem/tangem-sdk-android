package com.tangem.common.card

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Date

class CardCanResetPinTest {

    // region COS v8+

    @Test
    fun v8WithActiveBackupCanResetPin() {
        val card = createCard(firmwareVersion = FirmwareVersion.v8, backupStatus = Card.BackupStatus.Active(1))
        assertThat(card.canResetPin).isTrue()
    }

    @Test
    fun v8WithLinkedCardsCanResetPin() {
        val card = createCard(firmwareVersion = FirmwareVersion.v8, backupStatus = Card.BackupStatus.CardLinked(1))
        assertThat(card.canResetPin).isTrue()
    }

    @Test
    fun v8WithoutBackupCannotResetPin() {
        val card = createCard(firmwareVersion = FirmwareVersion.v8, backupStatus = Card.BackupStatus.NoBackup)
        assertThat(card.canResetPin).isFalse()
    }

    @Test
    fun v8WithUnknownBackupStatusCannotResetPin() {
        val card = createCard(firmwareVersion = FirmwareVersion.v8, backupStatus = null)
        assertThat(card.canResetPin).isFalse()
    }

    // endregion

    // region Before COS v8

    @Test
    fun preV8WithActiveBackupCanResetPin() {
        val card = createCard(firmwareVersion = PRE_V8, backupStatus = Card.BackupStatus.Active(1))
        assertThat(card.canResetPin).isTrue()
    }

    @Test
    fun preV8WithLinkedCardsCannotResetPin() {
        val card = createCard(firmwareVersion = PRE_V8, backupStatus = Card.BackupStatus.CardLinked(1))
        assertThat(card.canResetPin).isFalse()
    }

    @Test
    fun preV8WithoutBackupCannotResetPin() {
        val card = createCard(firmwareVersion = PRE_V8, backupStatus = Card.BackupStatus.NoBackup)
        assertThat(card.canResetPin).isFalse()
    }

    @Test
    fun preV8WithUnknownBackupStatusCannotResetPin() {
        val card = createCard(firmwareVersion = PRE_V8, backupStatus = null)
        assertThat(card.canResetPin).isFalse()
    }

    // endregion

    @Test
    fun v8StartsAtCos859() {
        assertThat(FirmwareVersion.v8).isEqualTo(FirmwareVersion(8, 59))
    }

    private fun createCard(firmwareVersion: FirmwareVersion, backupStatus: Card.BackupStatus?): Card = Card(
        cardId = "CB79000000018201",
        batchId = "CB79",
        cardPublicKey = ByteArray(0),
        firmwareVersion = firmwareVersion,
        manufacturer = Card.Manufacturer("TANGEM", Date(), null),
        issuer = Card.Issuer("TANGEM AG", ByteArray(0)),
        settings = Card.Settings(
            securityDelay = 0,
            maxWalletsCount = 0,
            mask = Card.SettingsMask(0),
        ),
        userSettings = UserSettings(
            isUserCodeRecoveryAllowed = true,
            isPINRequired = false,
            isNDEFDisabled = false,
        ),
        linkedTerminalStatus = Card.LinkedTerminalStatus.None,
        isAccessCodeSet = true,
        isPasscodeSet = false,
        supportedCurves = emptyList(),
        wallets = emptyList(),
        backupStatus = backupStatus,
    )

    private companion object {
        /** The last COS 8 build before the v8 protocol change */
        val PRE_V8 = FirmwareVersion(8, 58)
    }
}
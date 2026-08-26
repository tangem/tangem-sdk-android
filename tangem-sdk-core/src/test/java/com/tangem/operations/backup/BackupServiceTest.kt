package com.tangem.operations.backup

import com.google.common.truth.Truth
import com.tangem.TangemSdk
import com.tangem.common.CompletionResult
import com.tangem.common.StringsLocator
import com.tangem.common.UserCodeType
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.Config
import com.tangem.common.core.TangemSdkError
import com.tangem.common.json.MoshiJsonConverter
import com.tangem.common.services.secure.SecureStorage
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.util.Date

internal class BackupServiceTest {

    private val storage = InMemorySecureStorage()
    private val stringsLocator: StringsLocator = mockk(relaxed = true)
    private lateinit var repo: BackupRepo

    private fun createService(config: Config = Config()): BackupService {
        val sdk: TangemSdk = mockk()
        every { sdk.config } returns config
        every { sdk.secureStorage } returns storage

        repo = BackupRepo(storage = storage, jsonConverter = MoshiJsonConverter.INSTANCE)
        return BackupService(sdk = sdk, repo = repo, stringsLocator = stringsLocator)
    }

    @Test
    fun initialStateIsPreparing() {
        val service = createService()

        Truth.assertThat(service.currentState).isEqualTo(BackupService.State.Preparing)
        Truth.assertThat(service.canProceed).isFalse()
        Truth.assertThat(service.hasIncompletedBackup).isFalse()
        Truth.assertThat(service.accessCodeIsSet).isFalse()
        Truth.assertThat(service.passcodeIsSet).isFalse()
        Truth.assertThat(service.primaryCardIsSet).isFalse()
        Truth.assertThat(service.addedBackupCardsCount).isEqualTo(0)
        Truth.assertThat(service.canAddBackupCards).isFalse()
    }

    @Test
    fun setAccessCodeWithEmptyCodeReturnsAccessCodeRequired() {
        val service = createService()

        val result = service.setAccessCode("")

        Truth.assertThat(result).isInstanceOf(CompletionResult.Failure::class.java)
        Truth.assertThat((result as CompletionResult.Failure).error)
            .isInstanceOf(TangemSdkError.AccessCodeRequired::class.java)
        Truth.assertThat(service.accessCodeIsSet).isFalse()
    }

    @Test
    fun setAccessCodeWithDefaultValueReturnsAccessCodeCannotBeChanged() {
        val service = createService()

        val result = service.setAccessCode(UserCodeType.AccessCode.defaultValue)

        Truth.assertThat(result).isInstanceOf(CompletionResult.Failure::class.java)
        Truth.assertThat((result as CompletionResult.Failure).error)
            .isInstanceOf(TangemSdkError.AccessCodeCannotBeChanged::class.java)
        Truth.assertThat(service.accessCodeIsSet).isFalse()
    }

    @Test
    fun setAccessCodeWithValidCodeSucceeds() {
        val service = createService()

        val result = service.setAccessCode("123456")

        Truth.assertThat(result).isInstanceOf(CompletionResult.Success::class.java)
        Truth.assertThat(service.accessCodeIsSet).isTrue()
    }

    @Test
    fun setPasscodeWithEmptyCodeReturnsPasscodeRequired() {
        val service = createService()

        val result = service.setPasscode("")

        Truth.assertThat(result).isInstanceOf(CompletionResult.Failure::class.java)
        Truth.assertThat((result as CompletionResult.Failure).error)
            .isInstanceOf(TangemSdkError.PasscodeRequired::class.java)
        Truth.assertThat(service.passcodeIsSet).isFalse()
    }

    @Test
    fun setPasscodeWithDefaultValueReturnsPasscodeCannotBeChanged() {
        val service = createService()

        val result = service.setPasscode(UserCodeType.Passcode.defaultValue)

        Truth.assertThat(result).isInstanceOf(CompletionResult.Failure::class.java)
        Truth.assertThat((result as CompletionResult.Failure).error)
            .isInstanceOf(TangemSdkError.PasscodeCannotBeChanged::class.java)
        Truth.assertThat(service.passcodeIsSet).isFalse()
    }

    @Test
    fun setPasscodeWithValidCodeSucceeds() {
        val service = createService()

        val result = service.setPasscode("123456")

        Truth.assertThat(result).isInstanceOf(CompletionResult.Success::class.java)
        Truth.assertThat(service.passcodeIsSet).isTrue()
    }

    @Test
    fun setPrimaryCardUpdatesState() {
        val service = createService()

        service.setPrimaryCard(primaryCard(cardId = "primary_id"))

        Truth.assertThat(service.primaryCardIsSet).isTrue()
        Truth.assertThat(service.primaryCardId).isEqualTo("primary_id")
    }

    @Test
    fun addBackupCardWithoutPrimaryCardReturnsMissingPrimaryCard() {
        val service = createService()

        var result: CompletionResult<Card>? = null
        service.addBackupCard { result = it }

        Truth.assertThat(result).isInstanceOf(CompletionResult.Failure::class.java)
        Truth.assertThat((result as CompletionResult.Failure).error)
            .isInstanceOf(TangemSdkError.MissingPrimaryCard::class.java)
    }

    @Test
    fun proceedBackupInPreparingStateReturnsInvalidState() {
        val service = createService()

        var result: CompletionResult<Card>? = null
        service.proceedBackup { result = it }

        Truth.assertThat(result).isInstanceOf(CompletionResult.Failure::class.java)
        Truth.assertThat((result as CompletionResult.Failure).error)
            .isInstanceOf(TangemSdkError.BackupServiceInvalidState::class.java)
    }

    @Test
    fun discardSavedBackupResetsState() {
        val service = createService()
        service.setPrimaryCard(primaryCard())
        Truth.assertThat(service.primaryCardIsSet).isTrue()

        service.discardSavedBackup()

        Truth.assertThat(service.primaryCardIsSet).isFalse()
        Truth.assertThat(service.currentState).isEqualTo(BackupService.State.Preparing)
    }

    @Test
    fun canAddBackupCardsIsFalseWithoutPrimaryCard() {
        val service = createService()
        repo.data = repo.data.copy(backupCards = listOf(backupCard("backup_1")))

        Truth.assertThat(service.canAddBackupCards).isFalse()
    }

    @Test
    fun nonExtendedBackupLimitsToTwoCards() {
        val service = createService(config = Config(extendedBackup = false))
        repo.data = repo.data.copy(primaryCard = primaryCard(firmwareVersion = FirmwareVersion.v8))

        repo.data = repo.data.copy(backupCards = listOf(backupCard("backup_1")))
        Truth.assertThat(service.canAddBackupCards).isTrue()

        repo.data = repo.data.copy(
            backupCards = listOf(backupCard("backup_1"), backupCard("backup_2")),
        )
        Truth.assertThat(service.canAddBackupCards).isFalse()
    }

    @Test
    fun extendedBackupWithV8FirmwareAllowsThreeCards() {
        val service = createService(config = Config(extendedBackup = true))
        repo.data = repo.data.copy(primaryCard = primaryCard(firmwareVersion = FirmwareVersion.v8))

        repo.data = repo.data.copy(
            backupCards = listOf(backupCard("backup_1"), backupCard("backup_2")),
        )
        Truth.assertThat(service.canAddBackupCards).isTrue()

        repo.data = repo.data.copy(
            backupCards = listOf(backupCard("backup_1"), backupCard("backup_2"), backupCard("backup_3")),
        )
        Truth.assertThat(service.canAddBackupCards).isFalse()
    }

    @Test
    fun extendedBackupWithPreV8FirmwareLimitsToTwoCards() {
        val service = createService(config = Config(extendedBackup = true))
        repo.data = repo.data.copy(
            primaryCard = primaryCard(firmwareVersion = FirmwareVersion(major = 6, minor = 21)),
        )

        repo.data = repo.data.copy(
            backupCards = listOf(backupCard("backup_1"), backupCard("backup_2")),
        )

        Truth.assertThat(service.canAddBackupCards).isFalse()
    }

    @Test
    fun extendedBackupWithNullFirmwareLimitsToTwoCards() {
        val service = createService(config = Config(extendedBackup = true))
        repo.data = repo.data.copy(primaryCard = primaryCard(firmwareVersion = null))

        repo.data = repo.data.copy(
            backupCards = listOf(backupCard("backup_1"), backupCard("backup_2")),
        )

        Truth.assertThat(service.canAddBackupCards).isFalse()
    }

    private companion object {
        fun primaryCard(
            cardId: String = "primary_id",
            firmwareVersion: FirmwareVersion? = FirmwareVersion.v8,
        ): PrimaryCard = PrimaryCard(
            cardId = cardId,
            cardPublicKey = byteArrayOf(1, 2, 3),
            linkingKey = byteArrayOf(4, 5, 6),
            existingWalletsCount = 0,
            isHDWalletAllowed = true,
            issuer = Card.Issuer(name = "issuer", publicKey = byteArrayOf(7, 8, 9)),
            manufacturer = Card.Manufacturer(name = "TANGEM", manufactureDate = Date(), signature = null),
            walletCurves = emptyList(),
            batchId = null,
            firmwareVersion = firmwareVersion,
            isKeysImportAllowed = null,
            certificate = null,
        )

        fun backupCard(cardId: String): BackupCard = BackupCard(
            cardId = cardId,
            cardPublicKey = byteArrayOf(1, 2, 3),
            linkingKey = byteArrayOf(4, 5, 6),
            attestSignature = byteArrayOf(7, 8, 9),
            certificate = null,
        )
    }

    private class InMemorySecureStorage : SecureStorage {
        private val storage = mutableMapOf<String, ByteArray>()

        override fun get(account: String): ByteArray? = storage[account]

        override fun getAsString(key: String): String? = storage[key]?.let { String(it) }

        override fun store(key: String, value: String) {
            storage[key] = value.toByteArray()
        }

        override fun store(data: ByteArray, account: String) {
            storage[account] = data
        }

        override fun delete(account: String) {
            storage.remove(account)
        }

        override fun storeKey(key: ByteArray, account: String) {
            storage[account] = key
        }
    }
}
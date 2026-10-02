package com.tangem.sdk.extensions

import androidx.activity.ComponentActivity
import com.tangem.TangemSdk
import com.tangem.common.card.Card
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.json.MoshiJsonConverter
import com.tangem.common.services.secure.SecureStorage
import com.tangem.operations.backup.BackupRepo
import com.tangem.operations.backup.BackupService
import com.tangem.operations.backup.PrimaryCard
import com.tangem.sdk.AndroidStringLocator
import com.tangem.sdk.storage.create

@JvmOverloads
fun BackupService.Companion.init(
    sdk: TangemSdk,
    activity: ComponentActivity,
    primaryCardRunnableDecorator: (PrimaryCard, CardSessionRunnable<Card>) -> CardSessionRunnable<Card> =
        { _, runnable -> runnable },
): BackupService {
    return BackupService(
        sdk = sdk,
        repo = BackupRepo(SecureStorage.create(activity), MoshiJsonConverter.INSTANCE),
        stringsLocator = AndroidStringLocator(activity),
        primaryCardRunnableDecorator = primaryCardRunnableDecorator,
    )
}
package com.tangem.tester.app.common

import androidx.activity.ComponentActivity
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.tangem.Config
import com.tangem.Database
import com.tangem.TangemSdk
import com.tangem.common.CardValuesDbStorage
import com.tangem.tangem_sdk_new.DefaultSessionViewDelegate
import com.tangem.tangem_sdk_new.TerminalKeysStorage
import com.tangem.tangem_sdk_new.extensions.initNfcManager
import com.tangem.tangem_sdk_new.nfc.NfcManager
import com.tangem.tester.common.TangemSdkFactory

class DefaultTangemSdkFactory(
    private val activity: ComponentActivity,
    private val initialConfig: Config,
) : TangemSdkFactory {

    private var sdk: TangemSdk? = null
    private var nfcManager: NfcManager? = null

    override fun create(config: Config): TangemSdk {
//        nfcManager?.unregisterLifecycleObserver(activity)
        val manager = TangemSdk.initNfcManager(activity)
        nfcManager = manager
        val viewDelegate = DefaultSessionViewDelegate(manager, manager.reader)
        viewDelegate.sdkConfig = config
        viewDelegate.activity = activity

        val databaseDriver = AndroidSqliteDriver(Database.Schema, activity.applicationContext, "cards.db")
        sdk = TangemSdk(
            manager.reader, viewDelegate, config,
            CardValuesDbStorage(databaseDriver), TerminalKeysStorage(activity.application)
        )
        return sdk!!
    }

    override fun getSdk(): TangemSdk = sdk ?: create(initialConfig)
}
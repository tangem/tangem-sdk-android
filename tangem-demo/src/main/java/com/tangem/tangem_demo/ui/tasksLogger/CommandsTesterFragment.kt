package com.tangem.tangem_demo.ui.tasksLogger

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.tangem.*
import com.tangem.commands.PinType
import com.tangem.commands.file.FileSettings
import com.tangem.commands.file.FileSettingsChange
import com.tangem.common.CardValuesDbStorage
import com.tangem.common.CompletionResult
import com.tangem.tangem_demo.*
import com.tangem.tangem_demo.ui.BaseFragment
import com.tangem.tangem_demo.ui.tasksLogger.adapter.CommandSpinnerAdapter
import com.tangem.tangem_demo.ui.tasksLogger.adapter.RvConsoleAdapter
import com.tangem.tangem_sdk_new.TerminalKeysStorage
import com.tangem.tangem_sdk_new.extensions.initNfcManager
import com.tangem.tangem_sdk_new.nfc.NfcManager
import kotlinx.android.synthetic.main.fg_commands_tester.*

/**
[REDACTED_AUTHOR]
 */
class SdkTaskSpinnerFragment : BaseFragment() {

    private val rvConsoleAdapter = RvConsoleAdapter()
    private val logger = SdkLogger(rvConsoleAdapter)

    private lateinit var nfcManager: NfcManager
    private var commandState = ActiveCommandState()

    override fun initSdk(): TangemSdk {
        Log.addLogger(logger)
        val viewDelegate = EmptyViewDelegate()
        val activity = requireActivity()
        nfcManager = TangemSdk.initNfcManager(activity)

        val databaseDriver = AndroidSqliteDriver(Database.Schema, activity.applicationContext, "demo_cards.db")
        return TangemSdk(
            nfcManager.reader, viewDelegate, Config(), CardValuesDbStorage(databaseDriver),
            TerminalKeysStorage(activity.application))
    }

    override fun getLayoutId(): Int = R.layout.fg_commands_tester

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? DemoActivity)?.listenPageChanges {
            if (it == 1) {
                Log.addLogger(logger)
                handleCommandSelection(commandState.selectedType)
            } else {
                Log.removeLogger(logger)
                commandState.reset()
                nfcManager.reader.stopSession(true)
            }
        }
        initSpinner()
        initRecycler()
        btnClearConsole.setOnClickListener { rvConsoleAdapter.clear() }
    }

    private fun initSpinner() {
        spCommandSelector.adapter = CommandSpinnerAdapter(CommandType.values().toList())
        nfcManager.addTagDiscoveredListener(tagDiscoveredListener)
        postWorker {
            spCommandSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    // close the session from a previous command
                    if (commandState.isActive) nfcManager.reader.stopSession(true)

                    commandState.reset()
                    btnClearConsole.performClick()
                    handleCommandSelection(CommandType.values()[position])
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }
            spCommandSelector.setSelection(commandState.selectedType.ordinal, false)
        }
    }

    private fun initRecycler() {
        val linearLayoutManager = LinearLayoutManager(requireContext())

        rvConsole.layoutManager = linearLayoutManager
        rvConsole.adapter = rvConsoleAdapter
        rvConsole.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                (requireActivity() as? DemoActivity)?.enableSwipe(newState == RecyclerView.SCROLL_STATE_IDLE)
            }
        })
        rvConsoleAdapter.onItemCountChanged = { rvConsole.smoothScrollToPosition(it) }
    }

    private fun handleCommandSelection(type: CommandType) {
        if (commandState.isActive || !commandState.canBeReactivated) return

        commandState.selectedType = type
        commandState.isActive = true
        commandState.canBeReactivated = false

        // if don't use the workerThread then fragments is stuck during first initialization
        postWorker {
            when (type) {
                CommandType.Scan -> scanCard()
                CommandType.Sign -> sign(prepareHashesToSign())
                CommandType.WalletCreate -> createWallet()
                CommandType.WalletPurge -> purgeWallet()
                CommandType.IssuerDataRead -> readIssuerData()
                CommandType.IssuerDataWrite -> writeIssuerData()
                CommandType.IssuerExtraDataRead -> readIssuerExtraData()
                CommandType.IssuerExtraDataWrite -> writeIssuerExtraData()
                CommandType.UserDataRead -> readUserData()
                CommandType.UserDataWrite -> writeUserData()
                CommandType.SetPin1 -> setPin1()
                CommandType.SetPin2 -> setPin2()
                CommandType.FilesWriteSigned -> writeFilesSigned()
                CommandType.FilesWriteWithPassCode -> writeFilesWithPassCode()
                CommandType.FilesReadAll -> readFiles(true)
                CommandType.FilesReadPublic -> readFiles(false)
                CommandType.FilesDeleteAll -> deleteFiles()
                CommandType.FilesDeleteFirst -> deleteFiles(listOf(0))
                CommandType.FilesChangeSettingsFirstPublic -> {
                    changeFilesSettings(FileSettingsChange(0, FileSettings.Public))
                }
                CommandType.FilesChangeSettingsFirstPrivate -> {
                    changeFilesSettings(FileSettingsChange(0, FileSettings.Private))
                }
            }
        }
    }

    override fun handleCommandResult(result: CompletionResult<*>) {
        when (result) {
            is CompletionResult.Failure -> {
                if (result.error is TangemSdkError.UserCancelled) return
            }
        }
        commandState.isActive = false
    }

    private val tagDiscoveredListener = onDiscovered@{
        if (commandState.isActive) return@onDiscovered

        commandState.canBeReactivated = true
        handleCommandSelection(commandState.selectedType)
    }

    override fun onDestroy() {
        nfcManager.removeTagDiscoveredListener(tagDiscoveredListener)
        super.onDestroy()
    }

    class ActiveCommandState {
        var selectedType: CommandType = CommandType.Scan
        var isActive = false
        var canBeReactivated = true

        fun reset() {
            isActive = false
            canBeReactivated = true
        }
    }

    class EmptyViewDelegate : SessionViewDelegate {
        override fun onSessionStarted(cardId: String?, message: Message?, enableHowTo: Boolean) {}
        override fun onSecurityDelay(ms: Int, totalDurationSeconds: Int) {}
        override fun onDelay(total: Int, current: Int, step: Int) {}
        override fun onTagLost() {}
        override fun onTagConnected() {}
        override fun onWrongCard(wrongValueType: WrongValueType) {}
        override fun onSessionStopped(message: Message?) {}
        override fun onError(error: TangemError) {}
        override fun onPinRequested(pinType: PinType, callback: (pin: String) -> Unit) {}
        override fun onPinChangeRequested(pinType: PinType, callback: (pin: String) -> Unit) {}
        override fun setConfig(config: Config) {}
        override fun setMessage(message: Message?) {}
        override fun dismiss() {}
    }
}

enum class CommandType {
    Scan,
    Sign,
    WalletCreate,
    WalletPurge,
    IssuerDataRead,
    IssuerDataWrite,
    IssuerExtraDataRead,
    IssuerExtraDataWrite,
    UserDataRead,
    UserDataWrite,
    SetPin1,
    SetPin2,
    FilesWriteSigned,
    FilesWriteWithPassCode,
    FilesReadAll,
    FilesReadPublic,
    FilesDeleteAll,
    FilesDeleteFirst,
    FilesChangeSettingsFirstPublic,
    FilesChangeSettingsFirstPrivate,
}
package com.tangem.demo.ui.backup

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.tangem.TangemSdk
import com.tangem.common.CompletionResult
import com.tangem.demo.asFlow
import com.tangem.demo.postUi
import com.tangem.operations.backup.BackupService
import com.tangem.operations.backup.ResetBackupCommand
import com.tangem.sdk.extensions.init
import com.tangem.tangem_demo.databinding.ActivityBackupBinding
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.coroutines.CoroutineContext

class BackupActivity : AppCompatActivity() {

    lateinit var backupService: BackupService
    lateinit var tangemSdk: TangemSdk

    lateinit var binding: ActivityBackupBinding

    private val mainCoroutineContext: CoroutineContext
        get() = Job() + Dispatchers.Main + initCoroutineExceptionHandler()
    val mainScope = CoroutineScope(mainCoroutineContext)

    private fun initCoroutineExceptionHandler(): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val exceptionAsString: String = sw.toString()
            Log.e("Coroutine", exceptionAsString)
            throw throwable
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        tangemSdk = TangemSdk.init(this)
        backupService = BackupService.init(tangemSdk, this)
        backupService.discardSavedBackup()

        binding.btnAddBackupCard.setOnClickListener {
            backupService.addBackupCard { result ->
                postUi {
                    when (result) {
                        is CompletionResult.Success -> {
                            binding.tvAddBackupCard.text = "${backupService.addedBackupCardsCount} added."
                        }
                        is CompletionResult.Failure -> Unit
                    }
                }
            }
        }

        binding.btnAddOriginCard.setOnClickListener {
            backupService.readPrimaryCard { result ->
                postUi {
                    when (result) {
                        is CompletionResult.Success -> {
                            binding.btnAddOriginCard.text = "Good! You've scanned origin card."
                        }
                        is CompletionResult.Failure -> Unit
                    }
                }
            }
        }

        binding.tilAccessCode.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus && !binding.etAccessCode.text.isNullOrBlank()) {
                backupService.setAccessCode(binding.etAccessCode.text.toString())
            }
        }

        binding.etAccessCode.asFlow().debounce(timeoutMillis = 400)
            .filter { it.isNotBlank() }
            .onEach {
                backupService.setAccessCode(it)
            }
            .launchIn(mainScope)

        binding.btnStart.setOnClickListener {
            if (backupService.currentState != BackupService.State.Preparing) {
                backupService.proceedBackup {
                }
            }
        }

        binding.btnReset.setOnClickListener {
            tangemSdk.startSessionWithRunnable(ResetBackupCommand()) {
            }
        }
    }
}
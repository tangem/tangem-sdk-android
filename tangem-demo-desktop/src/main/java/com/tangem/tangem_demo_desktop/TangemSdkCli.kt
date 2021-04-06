import com.tangem.Log
import com.tangem.TangemError
import com.tangem.TangemSdk
import com.tangem.commands.CommandResponse
import com.tangem.commands.common.ResponseConverter
import com.tangem.common.CompletionResult
import com.tangem.common.extensions.hexToBytes
import org.apache.commons.cli.CommandLine

class TangemSdkCli(verbose: Boolean = false, indexOfTerminal: Int? = null, private val cmd: CommandLine) {

    private val sdk = TangemSdk.init(verbose, indexOfTerminal)
    private val responseConverter = ResponseConverter()

    fun execute(command: Command) {
        if (sdk == null) {
            println("There's no NFC terminal to execute command.")
            return
        }
        when (command) {
            Command.Read -> read(sdk)
            Command.Sign -> sign(sdk)
            Command.ReadIssuerData -> readIssuerData(sdk)
            Command.ReadIssuerExtraData -> readIssuerExtraData(sdk)
            Command.WriteIssuerData -> writeIssuerData(sdk)
            Command.WriteIssuerExtraData -> writeIssuerExtraData(sdk)
            Command.CreateWallet -> createWallet(sdk)
            Command.PurgeWallet -> purgeWallet(sdk)
        }
    }

    fun read(sdk: TangemSdk) {
        sdk.scanCard { result -> handleResult(result) }
    }

    private fun handleResult(result: CompletionResult<out CommandResponse>) {
        when (result) {
            is CompletionResult.Success -> {
                println(responseConverter.convertResponse(result.data))
            }
            is CompletionResult.Failure -> handleError(result.error)
        }
        Log.command { "Task completed" }
    }

    fun sign(sdk: TangemSdk) {
        val hashes = cmd.getOptionValue(TangemCommandOptions.Hashes.opt)
        val cid: String? = cmd.getOptionValue(TangemCommandOptions.CardId.opt)

        if (hashes == null) {
            println("Missing option value")
            return
        }

        sdk.sign(parseHashes(hashes), cardId = cid) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    println(responseConverter.convertResponse(result.data))
                }
                is CompletionResult.Failure -> handleError(result.error)
            }
            Log.command {  "Task completed" }
        }
    }

    private fun parseHashes(hashesArgument: String): Array<ByteArray> {
        return hashesArgument
                .split(",")
                .map { hash -> hash.hexToBytes() }
                .toTypedArray()
    }

    fun createWallet(sdk: TangemSdk) {
        val cid: String? = cmd.getOptionValue(TangemCommandOptions.CardId.opt)

        sdk.createWallet(cardId = cid) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    println(responseConverter.convertResponse(result.data))
                }
                is CompletionResult.Failure -> handleError(result.error)
            }
            Log.command { "Task completed" }
        }
    }

    fun purgeWallet(sdk: TangemSdk) {
        val cid: String? = cmd.getOptionValue(TangemCommandOptions.CardId.opt)

        sdk.purgeWallet(cardId = cid, walletIndex = null) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    println(responseConverter.convertResponse(result.data))
                }
                is CompletionResult.Failure -> handleError(result.error)
            }
            Log.command { "Task completed" }
        }
    }

    fun readIssuerData(sdk: TangemSdk) {
        val cid: String? = cmd.getOptionValue(TangemCommandOptions.CardId.opt)

        sdk.readIssuerData(cid) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    println(responseConverter.convertResponse(result.data))
                }
                is CompletionResult.Failure -> handleError(result.error)
            }
            Log.command { "Task completed" }
        }
    }

    fun readIssuerExtraData(sdk: TangemSdk) {
        val cid: String? = cmd.getOptionValue(TangemCommandOptions.CardId.opt)

        sdk.readIssuerExtraData(cid) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    println(responseConverter.convertResponse(result.data))
                }
                is CompletionResult.Failure -> handleError(result.error)
            }
            Log.command { "Task completed" }
        }
    }

    fun writeIssuerData(sdk: TangemSdk) {
        val cid: String? = cmd.getOptionValue(TangemCommandOptions.CardId.opt)
        val issuerData = cmd.getOptionValue(TangemCommandOptions.Data.opt)?.toByteArray()
        val counter = cmd.getOptionValue(TangemCommandOptions.Data.opt)?.toIntOrNull()
        val signedIssuerData = cmd.getOptionValue(TangemCommandOptions.Signature.opt)?.hexToBytes()

        if (issuerData == null || signedIssuerData == null) {
            println("Missing option value")
            return
        }

        sdk.writeIssuerData(cid, issuerData, signedIssuerData, counter) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    println(responseConverter.convertResponse(result.data))
                }
                is CompletionResult.Failure -> handleError(result.error)
            }
            Log.command { "Task completed" }
        }
    }

    fun writeIssuerExtraData(sdk: TangemSdk) {
        val cid: String? = cmd.getOptionValue(TangemCommandOptions.CardId.opt)
        val issuerData = cmd.getOptionValue(TangemCommandOptions.Data.opt)?.toByteArray()
        val counter = cmd.getOptionValue(TangemCommandOptions.Counter.opt)?.toIntOrNull()

        val startingSignature = cmd.getOptionValue(TangemCommandOptions.StartingSignature.opt)?.hexToBytes()
        val finalizingSignature = cmd.getOptionValue(TangemCommandOptions.FinalizingSignature.opt)?.hexToBytes()

        if (issuerData == null || startingSignature == null || finalizingSignature == null) {
            println("Missing option value")
            return
        }

        sdk.writeIssuerExtraData(cid, issuerData, startingSignature, finalizingSignature, counter) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    println(responseConverter.convertResponse(result.data))
                }
                is CompletionResult.Failure -> handleError(result.error)
            }
            Log.command { "Task completed" }
        }
    }

    private fun handleError(error: TangemError) {
        println("Task error: ${error.code}, ${error.javaClass.simpleName}")
    }
}


enum class Command(val value: String) {
    Read("read"),
    Sign("sign"),
    ReadIssuerData("readissuerdata"),
    ReadIssuerExtraData("readissuerextradata"),
    WriteIssuerData("writeissuerdata"),
    WriteIssuerExtraData("writeissuerextradata"),
    CreateWallet("createwallet"),
    PurgeWallet("purgewallet");

    companion object {
        private val values = values()
        fun byValue(value: String): Command? = values.find { it.value == value }
    }
}
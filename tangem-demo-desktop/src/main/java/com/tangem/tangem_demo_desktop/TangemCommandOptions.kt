import org.apache.commons.cli.Option
import org.apache.commons.cli.Options

enum class TangemCommandOptions(val option: Option) {

    Verbose(Option("v", "verbose", false, "provide detailed logs")),
    TerminalNumber(Option("t", "terminal", false, "terminal number")),
    Hashes(Option("hashes", true, "hashes to sign")),
    CardId(Option("cid", "cardid", true, "card id")),
    Data(Option("data", true, "data in hex format that will be written on a card")),
    Signature(Option("sig", "signature", true, "signature of issuer data")),
    StartingSignature(Option("startsig", true, "starting signature of issuer extra data")),
    FinalizingSignature(Option("finsig", true, "finalizing signature of issuer extra data")),
    Counter(Option("counter", true, "data counter"));

    val opt = option.opt
}

fun Command.generateOptions(): Options {
    val options = Options()
    options.addOption(TangemCommandOptions.Verbose.option)
    options.addOption(TangemCommandOptions.TerminalNumber.option)

    when (this) {
        Command.Read -> {}
        Command.Sign -> {
            options.addOption(TangemCommandOptions.Hashes.option)
            options.addOption(TangemCommandOptions.CardId.option)
        }
        Command.ReadIssuerData -> {
            options.addOption(TangemCommandOptions.CardId.option)
        }
        Command.ReadIssuerExtraData -> {
            options.addOption(TangemCommandOptions.CardId.option)
        }
        Command.WriteIssuerData -> {
            options.addOption(TangemCommandOptions.CardId.option)
            options.addOption(TangemCommandOptions.Data.option)
            options.addOption(TangemCommandOptions.Counter.option)
            options.addOption(TangemCommandOptions.Signature.option)
        }
        Command.WriteIssuerExtraData -> {
            options.addOption(TangemCommandOptions.CardId.option)
            options.addOption(TangemCommandOptions.Data.option)
            options.addOption(TangemCommandOptions.Counter.option)
            options.addOption(TangemCommandOptions.StartingSignature.option)
            options.addOption(TangemCommandOptions.FinalizingSignature.option)
        }
        Command.CreateWallet -> {
            options.addOption(TangemCommandOptions.CardId.option)
        }
        Command.PurgeWallet -> {
            options.addOption(TangemCommandOptions.CardId.option)
        }
    }
    return options
}

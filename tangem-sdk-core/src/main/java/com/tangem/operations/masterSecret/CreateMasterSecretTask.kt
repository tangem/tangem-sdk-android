package com.tangem.operations.masterSecret

import com.tangem.Log
import com.tangem.common.CompletionResult
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.guard
import com.tangem.crypto.hdWallet.bip32.ExtendedPrivateKey
import com.tangem.operations.derivation.DeriveWalletPublicKeysTask
import com.tangem.operations.read.ReadMasterSecretCommand
import com.tangem.operations.read.ReadMasterSecretResponse

/**
 * This task will create a new master secret on the card
 * A key pair is generated or imported and securely stored in the card.
 * @property privateKey: A private key to import.
 */
class CreateMasterSecretTask(
    private val mode: ManageMasterSecretMode = ManageMasterSecretMode.Create,
    private val privateKey: ExtendedPrivateKey? = null,
) : CardSessionRunnable<ReadMasterSecretResponse> {

    private var derivationTask: DeriveWalletPublicKeysTask? = null

    override fun run(session: CardSession, callback: CompletionCallback<ReadMasterSecretResponse>) {
        val command = ManageMasterSecretCommand(mode, privateKey)

        command.run(session) { result ->
            when (result) {
                is CompletionResult.Success -> callback(CompletionResult.Success(result.data))
                is CompletionResult.Failure -> {
                    if (result.error is TangemSdkError.InvalidState) {
                        // Wallet already created but we didn't get the proper response from the card.
                        // Rescan and retrieve the wallet
                        Log.debug { "Received wallet creation error. Try rescan and retrieve created wallet" }
                        scanAndRetrieveMasterSecret(session, callback)
                    } else {
                        callback(CompletionResult.Failure(result.error))
                    }
                }
            }
        }
    }

    private fun scanAndRetrieveMasterSecret(
        session: CardSession,
        callback: CompletionCallback<ReadMasterSecretResponse>,
    ) {
        val card = session.environment.card.guard {
            callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
            return
        }

        ReadMasterSecretCommand().run(session) { result ->
            when (result) {
                is CompletionResult.Success -> callback(
                    CompletionResult.Success(
                        ReadMasterSecretResponse(result.data.masterSecret)
                    )
                )
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }
        }
    }
}
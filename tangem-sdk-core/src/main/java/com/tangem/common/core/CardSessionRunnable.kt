package com.tangem.common.core

import com.tangem.common.CompletionResult
import com.tangem.common.encryption.EncryptionMode
import com.tangem.operations.PreflightReadMode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Basic interface for running tasks and [com.tangem.operations.Command] in a [CardSession]
 */
interface CardSessionRunnable<T> {
    /**
     * Allows SDK to fetch access code from the local encrypted repository when running the command
     * */
    val allowsRequestAccessCodeFromRepository: Boolean
        get() = true

    /**
     * Whether the session should handle access code prompting for this command.
     * When false, the command handles PIN by itself.
     */
    val shouldAskForAccessCode: Boolean
        get() = true

    /**
     * Required card session encryption level for v8+ secure channel.
     * Determines the secure channel elevation needed before executing the command.
     */
    val cardSessionEncryption: CardSessionEncryption
        get() = CardSessionEncryption.SECURE_CHANNEL

    // / An enforced encryption mode. Managed by a card if none. None by default.
    val encryptionMode: EncryptionMode
        get() = EncryptionMode.None

    /**
     * Mode for preflight read. Change this property only if you understand what to do
     */
    fun preflightReadMode(): PreflightReadMode = PreflightReadMode.FullCardRead

    /**
     *  This method will be called before nfc session starts.
     *  @param session:You can use view delegate methods at this moment, but not commands execution
     *  @param completion: Call the completion handler to complete the task.
     */
    fun prepare(session: CardSession, callback: CompletionCallback<Unit>) {
        callback(CompletionResult.Success(Unit))
    }

    /**
     * The starting point for custom business logic.
     * Implement this interface and use [TangemSdk.startSessionWithRunnable] to run.
     * @param session run commands in this [CardSession].
     * @param callback trigger the callback to complete the task.
     */
    fun run(session: CardSession, callback: CompletionCallback<T>)
}

typealias CompletionCallback<R> = (result: CompletionResult<R>) -> Unit

suspend fun <T> CardSessionRunnable<T>.prepare(session: CardSession): CompletionResult<Unit> =
    suspendCancellableCoroutine { continuation ->
        prepare(session) { result -> if (continuation.isActive) continuation.resume(result) }
    }

suspend fun <T> CardSessionRunnable<T>.run(session: CardSession): CompletionResult<T> =
    suspendCancellableCoroutine { continuation ->
        run(session) { result -> if (continuation.isActive) continuation.resume(result) }
    }
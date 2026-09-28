package com.tangem.common.authentication.keystore

/**
 * Defines when retrieving a key from the [KeystoreManager] asks the user to authenticate.
 */
enum class AuthenticationPolicy {

    /** Always prompts the user, even if the master key security delay is not reached. */
    Force,

    /** Reuses the authentication the master key still accepts; prompts only once its security delay has passed. */
    ReuseOrPrompt,

    /**
     * Reuses the authentication the master key still accepts and never prompts: once the security delay has
     * passed, the retrieval fails with [com.tangem.common.core.TangemSdkError.UserNotAuthenticated].
     */
    ReuseOnly,
}
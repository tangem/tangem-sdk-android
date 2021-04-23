package com.tangem.tester.executable.steps

import com.tangem.CardSessionRunnable
import com.tangem.commands.SignCommand
import com.tangem.commands.SignResponse
import com.tangem.commands.wallet.WalletIndex
import com.tangem.common.extensions.toHexString
import com.tangem.tester.common.ExecutableError
import com.tangem.tester.services.VariableService

/**
[REDACTED_AUTHOR]
 */
class SignStep : BaseStep<SignResponse>("SIGN_COMMAND") {

    companion object {
        private val keyHashes = "hashes"
        private val keyWalletIndex = "walletIndex"
        private val keySignatures = "signatures"
    }

    private lateinit var hashes: Array<ByteArray>
    private lateinit var walletIndex: WalletIndex

    override fun fetchVariables(name: String): ExecutableError.InitError? {
        return try {
            val rawHashes = VariableService.getValue(name, model.parameters[keyHashes]) as List<String>
            hashes = rawHashes.map { it.toByteArray() }.toTypedArray()

            val rawIndex = VariableService.getValue(name, model.parameters[keyWalletIndex]) as String
            val intIndex = rawIndex.toIntOrNull()
            walletIndex = if (intIndex == null) {
                WalletIndex.PublicKey(rawIndex.toByteArray())
            } else {
                WalletIndex.Index(intIndex)
            }
            null
        } catch (ex: Exception) {
            ExecutableError.InitError(ex.toString())
        }
    }

    override fun getRunnable(): CardSessionRunnable<SignResponse> = SignCommand(hashes, walletIndex)

    override fun checkForExpectedResult(result: SignResponse): ExecutableError.ExpectedResultError? {
        val resultStringSignatures = result.signatures.joinToString { it.toHexString() }
        if (model.expectedResult[keySignatures] != resultStringSignatures)
            return ExecutableError.ExpectedResultError(listOf("Signatures doesn't match"))

        return null
    }
}
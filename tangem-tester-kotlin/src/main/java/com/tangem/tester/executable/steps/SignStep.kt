package com.tangem.tester.executable.steps

import com.tangem.commands.SignResponse
import com.tangem.commands.wallet.WalletIndex
import com.tangem.common.extensions.toHexString
import com.tangem.tester.common.ExecutableError
import com.tangem.tester.variables.VariableService

/**
[REDACTED_AUTHOR]
 */
class SignStep : BaseStep<SignResponse>("SIGN_COMMAND") {

    companion object {
        private val keyHashes = "hashes"
        private val keyWalletIndex = "walletIndex"
        private val keySignatures = "signatures"
    }

    override fun fetchVariables(name: String): ExecutableError? {
        return try {
            val rawHashes = VariableService.getValue(name, model.parameters[keyHashes]) as List<String>
            val hashes = rawHashes.map { it.toByteArray() }.toTypedArray()

            val rawIndex = VariableService.getValue(name, model.parameters[keyWalletIndex]) as String
            val intIndex = rawIndex.toIntOrNull()
            val walletIndex = if (intIndex == null) {
                WalletIndex.PublicKey(rawIndex.toByteArray())
            } else {
                WalletIndex.Index(intIndex)
            }
            model.parameters[keyHashes] = hashes
            model.parameters[keyWalletIndex] = walletIndex
            null
        } catch (ex: Exception) {
            ExecutableError.FetchVariableError(ex.toString())
        }
    }

    override fun checkForExpectedResult(result: SignResponse): ExecutableError? {
        val resultStringSignatures = result.signatures.joinToString { it.toHexString() }
        if (model.expectedResult[keySignatures] != resultStringSignatures)
            return ExecutableError.ExpectedResultError(listOf("Signatures doesn't match"))

        return null
    }
}
package com.tangem.tester.common

interface VariableHolder {
    fun fetchVariables(name: String): ExecutableError?
}
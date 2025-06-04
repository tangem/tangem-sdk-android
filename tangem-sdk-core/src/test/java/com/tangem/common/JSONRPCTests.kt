package com.tangem.common

import com.tangem.Message
import com.tangem.common.card.Card
import com.tangem.common.card.CardWallet
import com.tangem.common.card.EllipticCurve
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.json.*
import com.tangem.crypto.bip39.BIP39Wordlist
import com.tangem.crypto.bip39.BIP39WordlistTest
import com.tangem.crypto.bip39.Wordlist
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.crypto.hdWallet.bip32.ExtendedPublicKey
import com.tangem.operations.attestation.AttestCardKeyResponse
import com.tangem.operations.derivation.ExtendedPublicKeysMap
import com.tangem.operations.files.File
import com.tangem.operations.files.FileSettings
import com.tangem.operations.files.FileVisibility
import com.tangem.operations.files.WriteFilesResponse
import com.tangem.operations.personalization.DepersonalizeResponse
import com.tangem.operations.sign.SignHashResponse
import com.tangem.operations.sign.SignHashesResponse
import com.tangem.operations.wallet.CreateWalletResponse
import org.junit.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Created by Anton Zhilenkov on 29/07/2021.
 */
class JSONRPCTests {
    private val converter = MoshiJsonConverter.INSTANCE
    private val jsonRpcConverter = JSONRPCConverter.shared(createDefaultWordlist())
    private val testCard: Card = initCard()

    private fun initCard(): Card {
        val json = readJson("Card")
        return converter.fromJson(json)!!
    }

    @Test
    fun testCard() {
        val json = readJson("Card")

        val mapFromJson = converter.toMap(json)
        val mapFromObject = converter.toMap(converter.toJson(testCard))
        assertTrue(jsonMapVerifier(mapFromJson, mapFromObject))
    }

    @Test
    fun testJsonRPCRequestParse() {
        JSONRPCRequest("{\"jsonrpc\": \"2.0\", \"method\": \"any\", \"params\": {}}")

        val json = "{\"jsonrpc\": \"2.0\", \"method\": \"subtract\", \"params\": {\"subtrahend\": 23, " +
            "\"minuend\": 42}, \"id\": 3}"
        val request = JSONRPCRequest(json)
        assertEquals(request.method, "subtract")
        assertEquals(request.params["subtrahend"], 23.0)
    }

    @Test
    fun testInitialMessageInit() {
        val json = "{\"header\": \"Some header\", \"body\": \"Some body\"}"

        val message: Message = converter.fromJson(json)!!
        assertEquals(message.header, "Some header")
        assertEquals(message.body, "Some body")
    }

    @Test
    fun testJsonResponse() {
        val response = SuccessResponse("c000111122223333")
        val result: CompletionResult<SuccessResponse> = CompletionResult.Success(response)
        val testResponse =
            "{\n \"jsonrpc\" : \"2.0\",\n \"result\" : {\n \"cardId\" : \"c000111122223333\"\n},\n\"id\" : 1\n}"

        val jsonRpcResponse = when (result) {
            is CompletionResult.Success -> JSONRPCResponse(result.data, null, 1)
            is CompletionResult.Failure -> JSONRPCResponse(null, result.error.toJSONRPCError(), 1)
        }
        val mapJsonResponse = converter.toMap(jsonRpcResponse.toJson())
        val mapTestJsonResponse = converter.toMap(testResponse)
        assertTrue(jsonMapVerifier(mapJsonResponse, mapTestJsonResponse))
    }

    @Test
    fun testPersonalize_v3ADA() {
        testMethod("Personalize_v3ADA", testCard)
    }

    @Test
    fun testPersonalize_v4() {
        testMethod("Personalize_v4", testCard)
    }

    @Test
    fun testDepersonalize() {
        val response = DepersonalizeResponse(true)
        testMethod("Depersonalize", response)
    }

    @Test
    fun testPreflightRead() {
        testMethod("PreflightRead", testCard)
    }

    @Test
    fun testScan() {
        testMethod("Scan", testCard)
    }

    @Test
    fun testCreateWallet() {
        val wallet = CardWallet(
            publicKey = "5130869115a2ff91959774c99d4dc2873f0c41af3e0bb23d027ab16d39de1348".hexToBytes(),
            chainCode = null,
            curve = EllipticCurve.Secp256r1,
            settings = CardWallet.Settings(true),
            totalSignedHashes = 10,
            remainingSignatures = 100,
            index = 1,
            isImported = false,
            hasBackup = false,
        )
        val response = CreateWalletResponse("c000111122223333", wallet)
        testMethod("CreateWallet", response)
    }

    @Test
    fun testImportWalletMnemonic() {
        val wallet = CardWallet(
            publicKey = "029983A77B155ED3B3B9E1DDD223BD5AA073834C8F61113B2F1B883AAA70971B5F".hexToBytes(),
            chainCode = "C7A888C4C670406E7AAEB6E86555CE0C4E738A337F9A9BC239F6D7E475110A4E".hexToBytes(),
            curve = EllipticCurve.Secp256k1,
            settings = CardWallet.Settings(true),
            totalSignedHashes = 10,
            remainingSignatures = 100,
            index = 1,
            isImported = false,
            hasBackup = false,
        )
        val response = CreateWalletResponse(cardId = "c000111122223333", wallet = wallet)
        testMethod(name = "ImportWalletMnemonic", response = response)
    }

    @Test
    fun testPurgeWallet() {
        val response = SuccessResponse("c000111122223333")
        testMethod("PurgeWallet", response)
    }

    @Test
    fun testSignHash() {
        val hash = "EB7411C2B7D871C06DAD51E58E44746583AD134F4E214E4899F2FC84802232A1".hexToBytes()
        val publicKey = "5130869115A2FF91959774C99D4DC2873F0C41AF3E0BB23D027AB16D39DE1348".hexToBytes()
        val response = SignHashResponse("c000111122223333", publicKey, hash, 1)
        testMethod("SignHash", response)
    }

    @Test
    fun testSignHashes() {
        val hashes = listOf(
            "EB7411C2B7D871C06DAD51E58E44746583AD134F4E214E4899F2FC84802232A1".hexToBytes(),
            "33443BD93F350B62A90A0C23D30C6D4E9BB164606E809CCACE60CF0E2591E58C".hexToBytes(),
        )
        val response = SignHashesResponse("c000111122223333", hashes, 2)
        testMethod("SignHashes", response)
    }

    @Test
    fun testSetAccessCode() {
        testMethod("SetAccessCode", null)
    }

    @Test
    fun testSetPasscode() {
        testMethod("SetPasscode", null)
    }

    @Test
    fun testDerivePublicKey() {
        val response = ExtendedPublicKey(
            publicKey = "0200300397571D99D41BB2A577E2CBE495C04AC5B9A97B7A4ECF999F23CE45E962".hexToBytes(),
            chainCode = "537F7361175B150732E17508066982B42D9FB1F8239C4D7BFC490088C83A8BBB".hexToBytes(),
        )
        testMethod("DeriveWalletPublicKey", response)
    }

    @Test
    fun testDerivePublicKeys() {
        val response = ExtendedPublicKeysMap(
            mapOf(
                DerivationPath("m/44'/0'") to ExtendedPublicKey(
                    publicKey = "0200300397571D99D41BB2A577E2CBE495C04AC5B9A97B7A4ECF999F23CE45E962".hexToBytes(),
                    chainCode = "537F7361175B150732E17508066982B42D9FB1F8239C4D7BFC490088C83A8BBB".hexToBytes(),
                ),
                DerivationPath("m/44'/1'") to ExtendedPublicKey(
                    publicKey = "0200300397571D99D41BB2A577E2CBE495C04AC5B9A97B7A4ECF999F23CE45E962".hexToBytes(),
                    chainCode = "537F7361175B150732E17508066982B42D9FB1F8239C4D7BFC490088C83A8BBB".hexToBytes(),
                ),
            ),
        )

        testMethod("DeriveWalletPublicKeys", response)
    }

    @Test
    fun testUserCodeRecoveryAllowed() {
        val response = SuccessResponse(cardId = "c000111122223333")
        testMethod("SetUserCodeRecoveryAllowed", response)
    }

    @Test
    fun testAttestCardKey() {
        val response = AttestCardKeyResponse(
            cardId = "c000111122223333",
            salt = "BBBBBBBBBBBB".hexToBytes(),
            cardSignature = "AAAAAAAAAAAA".hexToBytes(),
            challenge = "000000000000".hexToBytes(),
            linkedCardsPublicKeys = emptyList(),
        )
        testMethod("AttestCardKey", response)
    }

    @Test
    fun testFiles() {
        testMethod(
            name = "files/ReadFiles",
            response = listOf(
                File(
                    name = null,
                    data = "00AABBCCDD".hexToBytes(),
                    index = 0,
                    settings = FileSettings(false, FileVisibility.Public),
                    null,
                    null,
                ),
            ),
        )
        testMethod("files/DeleteFiles", SuccessResponse("c000111122223333"))
        testMethod("files/WriteFiles", WriteFilesResponse("c000111122223333", listOf(0, 1)))
        testMethod("files/ChangeFileSettings", SuccessResponse("c000111122223333"))
    }

    @Test
    fun testParseArrayOfRequests() {
        val fileText = readJson("TestArray")
        val fileMap = converter.toMap(fileText)
        val requestsJson = converter.toJson(fileMap["requests"])
        val linkersList = JSONRPCLinker.parse(requestsJson, converter)
        linkersList.forEach { it.initRunnable(jsonRpcConverter) }

        assertFalse(linkersList.any { it.hasError() })
    }

    private fun testMethod(name: String, response: Any?) {
        val jsonMap = converter.toMap(readJson(name))
        val jsonRequest = converter.toJson(jsonMap["request"])
        val request: JSONRPCRequest =
            assertDoesNotThrow("Json conversion failed to structure for $name") {
                JSONRPCRequest(jsonRequest)
            }

        assertDoesNotThrow("Conversion to JSONRPC Failed. File: $name") {
            jsonRpcConverter.convert(request)
        }

        val jsonResponse: JSONRPCResponse? =
            jsonMap["response"]?.let { converter.toJson(it).let { converter.fromJson(it) } }
        val result: CompletionResult<Any>? =
            response?.let { CompletionResult.Success(it) }
        if (jsonResponse != null && result != null) {
            val jsonResponseMap = converter.toMap(jsonResponse)
            val resultJsonRpcResponse = when (result) {
                is CompletionResult.Success -> JSONRPCResponse(result.data, null, jsonResponse.id)
                is CompletionResult.Failure -> JSONRPCResponse(
                    null,
                    result.error.toJSONRPCError(),
                    jsonResponse.id,
                )
            }
            val testResponseMap = converter.toMap(resultJsonRpcResponse)
            assertTrue(jsonMapVerifier(testResponseMap, jsonResponseMap))
        }
    }

    private fun readJson(fileName: String): String {
        val workingDir: Path = Paths.get("src/test/resources/jsonRpc", "$fileName.json")
        return String(Files.readAllBytes(workingDir))
    }

    @Suppress("ComplexMethod")
    private fun jsonMapVerifier(lMap: Map<String, *>, rMap: Map<String, *>): Boolean {
        val mapKeys = lMap.mapNotNull { if (rMap.containsKey(it.key)) it.key else null }
        if (lMap.size != mapKeys.size) error("Property counts not equals (${lMap.size} & ${mapKeys.size})")

        @Suppress("LoopWithTooManyJumpStatements")
        for (key in mapKeys) {
            val lValue = lMap[key]
            val rValue = rMap[key]

            if (lValue is Map<*, *> && rValue is Map<*, *>) {
                jsonMapVerifier(lValue as Map<String, *>, rValue as Map<String, *>)
                continue
            }

            if (lValue is List<*> && rValue is List<*>) {
                if (lValue.size != rValue.size) error("Arrays [$key] size not equals")

                lValue.forEachIndexed { index, lValueByIndex ->
                    val rValueByIndex = rValue[index]
                    if (lValueByIndex != rValueByIndex) {
                        error("Value in arrays [$key] not equals ($lValueByIndex & $rValueByIndex)")
                    }
                }
                continue
            }

            if (lValue is ByteArray && rValue is ByteArray) {
                if (lValue.contentEquals(rValue)) {
                    continue
                } else {
                    error("Values for [$key] not equals ($lValue & $rValue)")
                }
            }
            if (lValue == rValue) continue else error("Values for [$key] not equals ($lValue & $rValue)")
        }
        return true
    }

    private fun createDefaultWordlist(): Wordlist {
        val wordlistStream = getInputStreamForFile(BIP39WordlistTest.TEST_DICTIONARY_FILE_NAME)
        return BIP39Wordlist(wordlistStream)
    }

    private fun getInputStreamForFile(fileName: String): InputStream {
        return object {}.javaClass.classLoader.getResourceAsStream(fileName)!!
    }
}

package com.tangem.tester.app

import android.content.res.AssetManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tangem.Config
import com.tangem.TangemSdk
import com.tangem.commands.common.jsonConverter.MoshiJsonConverter
import com.tangem.json.JsonAdaptersFactory
import com.tangem.tangem_sdk_new.extensions.init
import com.tangem.tester.CardTester
import com.tangem.tester.app.common.DefaultTangemSdkFactory
import kotlinx.android.synthetic.main.activity_main.*

class TesterMainActivity : AppCompatActivity() {

    private lateinit var sdk: TangemSdk
    private lateinit var jsonAdaptersFactory: JsonAdaptersFactory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        init()
        initButton()
    }

    private fun init() {
        sdk = TangemSdk.init(this)
        jsonAdaptersFactory = JsonAdaptersFactory()
    }

    private fun initButton() {
        btnTest.setOnClickListener {
//            executeScan()
            executeTest()
        }
    }

    private fun executeScan(){
        val jsonConverter = MoshiJsonConverter.tangemSdkJsonConverter()
        val json = loadJsonFile("scan_method") ?: return
        val mapJson: Map<String, Any> = jsonConverter.toMap(jsonConverter.fromJson(json))

        val adaptersFactory = JsonAdaptersFactory()
        val sdkAdapter = adaptersFactory.createFrom(mapJson) ?: return

        sdk.startSessionWithRunnable(sdkAdapter) {
            //done
        }
    }

    private fun executeTest() {
        val json = loadJsonFile("scan_test") ?: return

        val tester = CardTester(DefaultTangemSdkFactory(this, Config()))
        tester.runFromJson(json)

    }

    private fun loadJsonFile(name: String): String? = assets.readAssetsFile("$name.json")
}

fun AssetManager.readAssetsFile(fileName: String): String? = try {
    open(fileName).bufferedReader().use { it.readText() }
} catch (ex: Exception) {
    null
}

fun timing(any: () -> Any?, logger: ((Long) -> Unit)? = null): Any? {
    val start = System.currentTimeMillis()
    val result = any()
    val measurementResult = System.currentTimeMillis() - start
    if (logger == null) print("Measure: $measurementResult") else logger(measurementResult)
    return result
}
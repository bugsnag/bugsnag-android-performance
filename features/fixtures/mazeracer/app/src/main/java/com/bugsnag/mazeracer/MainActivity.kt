package com.bugsnag.mazeracer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Window
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.PerformanceConfiguration
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private val apiKeyKey = "BUGSNAG_API_KEY"
    private val mainHandler = Handler(Looper.getMainLooper())

    lateinit var prefs: SharedPreferences

    var scenario: Scenario? = null
    var polling = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log("MainActivity.onCreate called")
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_main)
        prefs = getPreferences(Context.MODE_PRIVATE)

        // Attempt to dismiss any system dialogs (such as "MazeRunner crashed")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            log("Broadcast ACTION_CLOSE_SYSTEM_DIALOGS intent")
            @Suppress("DEPRECATION")
            val closeDialog = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            sendBroadcast(closeDialog)
        }

        log("Set up clearUserData click handler")
        val clearUserData = findViewById<Button>(R.id.clearUserData)
        clearUserData.setOnClickListener {
            clearStoredApiKey()
            val apiKeyField = findViewById<EditText>(R.id.manualApiKey)
            apiKeyField.text.clear()
            log("Cleared user data")
        }

        if (apiKeyStored()) {
            log("Using stored API key")
            val apiKey = getStoredApiKey()
            val apiKeyField = findViewById<EditText>(R.id.manualApiKey)
            apiKeyField.text.clear()
            apiKeyField.text.append(apiKey)
        }
        log("MainActivity.onCreate complete")
    }

    override fun onResume() {
        super.onResume()
        log("MainActivity.onResume called")

        if (!polling) {
            startCommandRunner()
        }
        log("MainActivity.onResume complete")
    }

    // Checks general internet and secure tunnel connectivity
    private fun checkNetwork() {
        log("Checking network connectivity")
        try {
            URL("https://www.google.com").readText()
            log("Connection to www.google.com seems ok")
        } catch (e: Exception) {
            log("Connection to www.google.com FAILED", e)
        }

        try {
            URL("http://bs-local.com:9339").readText()
            log("Connection to Maze Runner seems ok")
        } catch (e: Exception) {
            log("Connection to Maze Runner FAILED", e)
        }
    }

    // Starts a thread to poll for Maze Runner actions to perform
    private fun startCommandRunner() {
        // Get the next maze runner command
        polling = true
        thread(start = true) {
            checkNetwork()

            while (polling) {
                Thread.sleep(1000)
                try {
                    // Get the next command from Maze Runner
                    val commandStr = readCommand()
                    if (commandStr == "null") {
                        log("No Maze Runner commands queued")
                        continue
                    }

                    // Log the received command
                    log("Received command: $commandStr")
                    val command = JSONObject(commandStr)
                    val action = command.getString("action")
                    val scenarioName = command.getString("scenario_name")
                    val scenarioMetadata = command.getString("scenario_metadata")
                    val endpointUrl = command.getString("endpoint")
                    log("command.action: $action")
                    log("command.scenarioName: $scenarioName")
                    log("command.scenarioMode: $scenarioMetadata")
                    log("command.endpoint: $endpointUrl")

                    mainHandler.post {
                        // Display some feedback of the action being run on he UI
                        val actionField = findViewById<EditText>(R.id.command_action)
                        val scenarioField = findViewById<EditText>(R.id.command_scenario)
                        actionField.setText(action)
                        scenarioField.setText(scenarioName)

                        // Perform the given action on the UI thread
                        when (action) {
                            "run_scenario" -> {
                                polling = false
                                runScenario(scenarioName, scenarioMetadata, endpointUrl)
                            }
                            else -> throw IllegalArgumentException("Unknown action: $action")
                        }
                    }
                } catch (e: Exception) {
                    log("Failed to fetch command from Maze Runner", e)
                }
            }
        }
    }

    // execute the pre-loaded scenario, or load it then execute it if needed
    private fun runScenario(
        scenarioName: String,
        scenarioMetadata: String,
        endpointUrl: String
    ) {
        if (scenario == null) {
            scenario = loadScenario(scenarioName, scenarioMetadata, endpointUrl)
        }

        /**
         * Enqueues the test case with a delay on the main thread. This avoids the Activity wrapping
         * unhandled Exceptions
         */
        mainHandler.post {
            log("Executing scenario: $scenarioName")
            scenario?.startScenario()
        }
    }

    private fun readCommand(): String {
        val commandUrl = "http://bs-local.com:9339/command"
        val urlConnection = URL(commandUrl).openConnection() as HttpURLConnection
        try {
            return urlConnection.inputStream.use { it.reader().readText() }
        } catch (ioe: IOException) {
            try {
                val errorMessage = urlConnection.errorStream.use { it.reader().readText() }
                log(
                    "Failed to GET $commandUrl (HTTP ${urlConnection.responseCode} " +
                        "${urlConnection.responseMessage}):\n" +
                        "${"-".repeat(errorMessage.width)}\n" +
                        "$errorMessage\n" +
                        "-".repeat(errorMessage.width)
                )
            } catch (e: Exception) {
                log("Failed to retrieve error message from connection", e)
            }

            throw ioe
        }
    }

    private fun prepareConfig(apiKey: String, endpoint: String): PerformanceConfiguration {
        return PerformanceConfiguration(applicationContext).also { config ->
            config.apiKey = apiKey
            config.endpoint = endpoint
            config.autoInstrumentActivities = AutoInstrument.OFF
        }
    }

    private fun loadScenario(
        scenarioName: String,
        scenarioMetadata: String,
        endpoint: String
    ): Scenario {
        log("loadScenario($scenarioName, $scenarioMetadata, $endpoint)")
        val apiKeyField = findViewById<EditText>(R.id.manualApiKey)

        val manualMode = apiKeyField.text.isNotEmpty()
        val apiKey = when {
            manualMode -> apiKeyField.text.toString()
            else -> "a35a2a72bd230ac0aa0f52715bbdc6aa"
        }

        if (manualMode) {
            log("Running in manual mode with API key: $apiKey")
            setStoredApiKey(apiKey)
        }

        log("prepareConfig($apiKey, $endpoint)")
        val config = prepareConfig(apiKey, endpoint)

        log("Scenario.load($config, $scenarioName, $scenarioMetadata)")
        return Scenario.load(config, scenarioName, scenarioMetadata).apply {
            context = this@MainActivity
        }
    }

    private fun apiKeyStored() = prefs.contains(apiKeyKey)

    private fun setStoredApiKey(apiKey: String) {
        with(prefs.edit()) {
            putString(apiKeyKey, apiKey)
            commit()
        }
    }

    private fun clearStoredApiKey() {
        with(prefs.edit()) {
            remove(apiKeyKey)
            commit()
        }
    }

    private fun getStoredApiKey() = prefs.getString(apiKeyKey, "")

    private val String.width
        get() = lineSequence().fold(0) { maxWidth, line -> kotlin.math.max(maxWidth, line.length) }
}

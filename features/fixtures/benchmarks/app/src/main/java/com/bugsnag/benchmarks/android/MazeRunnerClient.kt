package com.bugsnag.benchmarks.android

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

open class MazeRunnerClient(
    val context: Context,
    val configFileTimeout: Long = 5000L,
) {
    private var mazeAddress: String? = null

    var latestCommandId: String = ""
        private set

    open fun log(message: String, throwable: Throwable? = null) {
        Log.d("MazeRunnerClient", message, throwable)
    }

    open fun getEndpoint(path: String): String {
        return "http://$mazeAddress/path"
    }

    protected open suspend fun awaitMazeRunnerEndpoint(): String = withContext(Dispatchers.IO) {
        val externalFilesDir = context.getExternalFilesDir(null)
        val configFile = File(externalFilesDir, "fixture_config.json")
        log("Attempting to read Maze Runner address from config file ${configFile.path}")

        var mazeAddress: String? = null
        // Poll for the fixture config file
        val pollEnd = System.currentTimeMillis() + configFileTimeout
        while (System.currentTimeMillis() < pollEnd) {
            if (configFile.exists()) {
                val fileContents = configFile.readText()
                val fixtureConfig = runCatching { JSONObject(fileContents) }.getOrNull()
                mazeAddress = fixtureConfig?.optString("maze_address")
                if (!mazeAddress.isNullOrBlank()) {
                    log("Maze Runner address set from config file: $mazeAddress")
                    break
                }
            }

            delay(250L)
        }

        // Assume we are running in legacy mode on BrowserStack
        if (mazeAddress.isNullOrBlank()) {
            log("Failed to read Maze Runner address from config file, reverting to legacy BrowserStack address")
            mazeAddress = "bs-local.com:9339"
        }

        return@withContext mazeAddress
    }

    fun CoroutineScope.commands(): ReceiveChannel<MazeRunnerCommand> {
        val commandChannel = Channel<MazeRunnerCommand>()

        launch(Dispatchers.Default) {
            if (mazeAddress == null) {
                mazeAddress = awaitMazeRunnerEndpoint()
            }

            var polling = true
            while (polling) {
                val nextCommand: MazeRunnerCommand? =
                    withContext(Dispatchers.IO) { tryReadCommand() }

                @Suppress("OPT_IN_USAGE")
                if (nextCommand != null) {
                    log("Sending command to channel: $nextCommand")
                    commandChannel.trySend(nextCommand).onClosed {
                        polling = false
                    }
                } else if (!commandChannel.isClosedForSend) {
                    log("No command received, waiting before polling again")
                    delay(1000L)
                } else {
                    log("Command channel is closed, stopping polling")
                    polling = false
                }
            }
        }

        return commandChannel
    }

    suspend fun readNextCommand(after: String = latestCommandId): MazeRunnerCommand {
        // Get the next maze runner command
        var command: MazeRunnerCommand?
        do {
            command = withContext(Dispatchers.IO) { tryReadCommand(after) }
            if (command == null) {
                log("No command received, waiting before polling again")
                delay(250L) // Wait before polling again
            }
        } while (command == null)
        return command
    }

    private fun tryReadCommand(after: String = latestCommandId): MazeRunnerCommand? {
        try {
            // Get the next command from Maze Runner
            val commandStr = readCommand(after)
            if (commandStr == "null") {
                log("No Maze Runner commands queued")
                return null
            }

            // Log the received command
            log("Received command: $commandStr")
            val command = MazeRunnerCommand(JSONObject(commandStr))
            val id = command.id

            if (!id.isNullOrEmpty()) {
                latestCommandId = id
                log("Latest command ID updated to: $latestCommandId")
            }

            if (command.isNoOp) {
                log("noop - looping around for another poll()")
                // immediately loop around
                return null
            }

            return command
        } catch (e: Exception) {
            log("Failed to fetch command from Maze Runner", e)
            return null
        }
    }

    suspend fun reportMetrics(metrics: Map<String, String>) = withContext(Dispatchers.IO) {
        if (mazeAddress == null) {
            mazeAddress = awaitMazeRunnerEndpoint()
        }

        val metricsUrl = "http://$mazeAddress/metrics"
        val urlConnection = URL(metricsUrl).openConnection() as HttpURLConnection
        try {
            urlConnection.requestMethod = "POST"
            urlConnection.setRequestProperty("Content-Type", "application/json")
            urlConnection.doOutput = true

            urlConnection.outputStream.use { outputStream ->
                outputStream.write(JSONObject(metrics).toString().toByteArray())
            }

            val responseCode = urlConnection.responseCode
            log("Metrics sent to $metricsUrl, response code: $responseCode")
        } catch (ioe: IOException) {
            log("Failed to send metrics to $metricsUrl", ioe)
        } finally {
            urlConnection.disconnect()
        }
    }

    private fun readCommand(after: String): String {
        val commandUrl = "http://$mazeAddress/command?after=$after"
        val urlConnection = URL(commandUrl).openConnection() as HttpURLConnection
        try {
            return urlConnection.inputStream.use { it.reader().readText() }
        } catch (ioe: IOException) {
            try {
                val errorMessage = urlConnection.errorStream.use { it.reader().readText() }
                log(
                    """
                    Failed to GET $commandUrl (HTTP ${urlConnection.responseCode} ${urlConnection.responseMessage}):
                    ${"-".repeat(errorMessage.width)}
                    $errorMessage
                    ${"-".repeat(errorMessage.width)}
                    """.trimIndent(),
                )
            } catch (e: Exception) {
                log("Failed to retrieve error message from connection", e)
            }

            throw ioe
        }
    }

    private val String.width
        get() = lineSequence().maxOf { it.length }
}

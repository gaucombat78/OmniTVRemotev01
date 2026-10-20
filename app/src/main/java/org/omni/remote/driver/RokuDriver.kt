package org.omni.remote.driver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.omni.remote.core.model.RemoteCommand
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class RokuDriver(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(500, TimeUnit.MILLISECONDS)
        .readTimeout(1000, TimeUnit.MILLISECONDS)
        .build()
) : TvDriver {

    private var baseUrl: String = ""
    private var connected: Boolean = false

    override suspend fun connect(ipAddress: String, token: String?): Result<String?> = withContext(Dispatchers.IO) {
        baseUrl = "http://$ipAddress:8060"
        try {
            val req = Request.Builder().url("$baseUrl/query/device-info").get().build()
            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    connected = true
                    Result.success(null)
                } else {
                    Result.failure(Exception("Roku ping rejected with code ${response.code}"))
                }
            }
        } catch (e: Exception) {
            connected = false
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        connected = false
    }

    override suspend fun isConnected(): Boolean = connected

    override suspend fun sendCommand(command: RemoteCommand): Result<Unit> = withContext(Dispatchers.IO) {
        val endpoint = when (command) {
            RemoteCommand.POWER -> "keypress/Power"
            RemoteCommand.UP -> "keypress/Up"
            RemoteCommand.DOWN -> "keypress/Down"
            RemoteCommand.LEFT -> "keypress/Left"
            RemoteCommand.RIGHT -> "keypress/Right"
            RemoteCommand.OK -> "keypress/Select"
            RemoteCommand.BACK -> "keypress/Back"
            RemoteCommand.HOME -> "keypress/Home"
            RemoteCommand.VOL_UP -> "keypress/VolumeUp"
            RemoteCommand.VOL_DOWN -> "keypress/VolumeDown"
            RemoteCommand.MUTE -> "keypress/VolumeMute"
            RemoteCommand.PLAY_PAUSE, RemoteCommand.PLAY, RemoteCommand.PAUSE -> "keypress/Play"
            else -> return@withContext Result.failure(UnsupportedOperationException("Unsupported Roku key"))
        }

        try {
            val req = Request.Builder()
                .url("$baseUrl/$endpoint")
                .post("".toRequestBody("text/plain".toMediaType()))
                .build()
            client.newCall(req).execute().close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun launchAppAndSearch(app: SupportedApp, query: String): Result<Unit> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val endpoint = when (app) {
            SupportedApp.PRIME_VIDEO -> if (query.isNotEmpty()) "$baseUrl/search/browse?keyword=$encodedQuery&type=video" else "$baseUrl/launch/13"
            SupportedApp.YOUTUBE -> "$baseUrl/launch/837?contentId=$encodedQuery"
            SupportedApp.NETFLIX -> "$baseUrl/launch/12"
            SupportedApp.SPOTIFY -> "$baseUrl/launch/22271"
        }

        try {
            val req = Request.Builder()
                .url(endpoint)
                .post("".toRequestBody("text/plain".toMediaType()))
                .build()
            client.newCall(req).execute().close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendKeySequence(text: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            for (char in text) {
                val encodedChar = URLEncoder.encode(char.toString(), "UTF-8")
                val req = Request.Builder()
                    .url("$baseUrl/keypress/Lit_$encodedChar")
                    .post("".toRequestBody("text/plain".toMediaType()))
                    .build()
                client.newCall(req).execute().close()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

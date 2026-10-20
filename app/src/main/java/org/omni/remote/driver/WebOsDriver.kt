package org.omni.remote.driver

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import org.omni.remote.core.model.RemoteCommand
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class WebOsDriver : TvDriver, WebSocketListener() {

    private var webSocket: WebSocket? = null
    private val reqCounter = AtomicInteger(1)
    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private var pairingDeferred: CompletableDeferred<String?>? = null
    private var isSocketOpen = false

    private val unsafeClient: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("SSL").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    override suspend fun connect(ipAddress: String, token: String?): Result<String?> = withContext(Dispatchers.IO) {
        disconnect()
        pairingDeferred = CompletableDeferred()

        val request = Request.Builder().url("wss://$ipAddress:3000/").build()
        webSocket = unsafeClient.newWebSocket(request, this@WebOsDriver)

        val handshakeJson = createRegisterPayload(token)
        webSocket?.send(handshakeJson.toString())

        try {
            val registeredToken = pairingDeferred?.await()
            Result.success(registeredToken)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        isSocketOpen = false
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        pendingResponses.clear()
    }

    override suspend fun isConnected(): Boolean = isSocketOpen

    override fun onOpen(webSocket: WebSocket, response: Response) {
        isSocketOpen = true
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val json = JSONObject(text)
        val type = json.optString("type")
        val id = json.optString("id")

        if (type == "registered") {
            val clientKey = json.optJSONObject("payload")?.optString("client-key")
            pairingDeferred?.complete(clientKey)
        }

        if (pendingResponses.containsKey(id)) {
            pendingResponses.remove(id)?.complete(json)
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        isSocketOpen = false
        pairingDeferred?.completeExceptionally(t)
    }

    override suspend fun sendCommand(command: RemoteCommand): Result<Unit> {
        val uri = when (command) {
            RemoteCommand.VOL_UP -> "ssap://audio/volumeUp"
            RemoteCommand.VOL_DOWN -> "ssap://audio/volumeDown"
            RemoteCommand.MUTE -> "ssap://audio/setMute"
            RemoteCommand.POWER -> "ssap://system/turnOff"
            RemoteCommand.PLAY -> "ssap://media.controls/play"
            RemoteCommand.PAUSE -> "ssap://media.controls/pause"
            RemoteCommand.STOP -> "ssap://media.controls/stop"
            RemoteCommand.HOME -> "ssap://system.launcher/open"
            RemoteCommand.BACK -> "ssap://system.launcher/back"
            else -> return Result.failure(UnsupportedOperationException("Direction keys require pointer socket"))
        }
        return sendRequest(uri, JSONObject())
    }

    override suspend fun launchAppAndSearch(app: SupportedApp, query: String): Result<Unit> {
        val appId = when (app) {
            SupportedApp.PRIME_VIDEO -> "amazon"
            SupportedApp.YOUTUBE -> "youtube.leanback.v4"
            SupportedApp.NETFLIX -> "netflix"
            SupportedApp.SPOTIFY -> "spotify-beehive"
        }

        val params = JSONObject().apply {
            put("id", appId)
            if (app == SupportedApp.YOUTUBE && query.isNotEmpty()) {
                put("contentId", "https://www.youtube.com/results?search_query=$query")
            } else if (app == SupportedApp.PRIME_VIDEO && query.isNotEmpty()) {
                put("contentId", "https://app.primevideo.com/search?phrase=$query")
            }
        }
        return sendRequest("ssap://system.launcher/launch", params)
    }

    override suspend fun sendKeySequence(text: String): Result<Unit> {
        val params = JSONObject().apply {
            put("text", text)
            put("replace", 0)
        }
        return sendRequest("ssap://tv/insertText", params)
    }

    private suspend fun sendRequest(uri: String, payload: JSONObject): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isSocketOpen) return@withContext Result.failure(IllegalStateException("Socket disconnected"))
        val id = "req_${reqCounter.getAndIncrement()}"
        val envelope = JSONObject().apply {
            put("id", id)
            put("type", "request")
            put("uri", uri)
            put("payload", payload)
        }

        val deferred = CompletableDeferred<JSONObject>()
        pendingResponses[id] = deferred
        webSocket?.send(envelope.toString())

        try {
            deferred.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createRegisterPayload(token: String?): JSONObject {
        val root = JSONObject()
        root.put("type", "register")
        root.put("id", "register_0")

        val payload = JSONObject()
        payload.put("forcePairing", false)
        payload.put("pairingType", "PROMPT")
        if (!token.isNullOrEmpty()) {
            payload.put("client-key", token)
        }

        val manifest = JSONObject().apply {
            put("permissions", arrayOf(
                "LAUNCH", "CONTROL_AUDIO", "CONTROL_POWER", "READ_INSTALLED_APPS",
                "CONTROL_INPUT_TEXT", "CHECK_BLUETOOTH_DEVICE"
            ))
        }
        payload.put("manifest", manifest)
        root.put("payload", payload)
        return root
    }
}

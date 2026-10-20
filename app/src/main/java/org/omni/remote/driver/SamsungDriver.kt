package org.omni.remote.driver

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import org.omni.remote.core.model.RemoteCommand
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class SamsungDriver : TvDriver, WebSocketListener() {

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var tokenCallback: ((String) -> Unit)? = null

    private val unsafeClient: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    override suspend fun connect(ipAddress: String, token: String?): Result<String?> = withContext(Dispatchers.IO) {
        val appNameEncoded = Base64.encodeToString("OmniRemote".toByteArray(), Base64.NO_WRAP)
        var url = "wss://$ipAddress:8002/api/v2/channels/samsung.remote.control?name=$appNameEncoded"
        if (!token.isNullOrEmpty()) {
            url += "&token=$token"
        }

        val request = Request.Builder().url(url).build()
        webSocket = unsafeClient.newWebSocket(request, this@SamsungDriver)
        Result.success(token)
    }

    override suspend fun disconnect() {
        isConnected = false
        webSocket?.close(1000, "Exit")
        webSocket = null
    }

    override suspend fun isConnected(): Boolean = isConnected

    override fun onOpen(webSocket: WebSocket, response: Response) {
        isConnected = true
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val json = JSONObject(text)
        if (json.optString("event") == "ms.channel.connect") {
            val data = json.optJSONObject("data")
            val newToken = data?.optString("token")
            if (!newToken.isNullOrEmpty()) {
                tokenCallback?.invoke(newToken)
            }
        }
    }

    override suspend fun sendCommand(command: RemoteCommand): Result<Unit> = withContext(Dispatchers.IO) {
        val key = when (command) {
            RemoteCommand.POWER -> "KEY_POWER"
            RemoteCommand.UP -> "KEY_UP"
            RemoteCommand.DOWN -> "KEY_DOWN"
            RemoteCommand.LEFT -> "KEY_LEFT"
            RemoteCommand.RIGHT -> "KEY_RIGHT"
            RemoteCommand.OK -> "KEY_ENTER"
            RemoteCommand.HOME -> "KEY_HOME"
            RemoteCommand.BACK -> "KEY_RETURN"
            RemoteCommand.VOL_UP -> "KEY_VOLUP"
            RemoteCommand.VOL_DOWN -> "KEY_VOLDOWN"
            RemoteCommand.MUTE -> "KEY_MUTE"
            RemoteCommand.PLAY -> "KEY_PLAY"
            RemoteCommand.PAUSE -> "KEY_PAUSE"
            else -> return@withContext Result.failure(IllegalArgumentException("Unsupported command"))
        }

        val envelope = JSONObject().apply {
            put("method", "ms.remote.control")
            put("params", JSONObject().apply {
                put("Cmd", "Click")
                put("DataOfCmd", key)
                put("Option", "false")
                put("TypeOfRemote", "SendRemoteKey")
            })
        }
        webSocket?.send(envelope.toString())
        Result.success(Unit)
    }

    override suspend fun launchAppAndSearch(app: SupportedApp, query: String): Result<Unit> = withContext(Dispatchers.IO) {
        val appId = when(app) {
            SupportedApp.PRIME_VIDEO -> "3201512006785"
            SupportedApp.YOUTUBE -> "111299001912"
            SupportedApp.NETFLIX -> "3201512006785"
            else -> return@withContext Result.failure(UnsupportedOperationException())
        }
        
        val envelope = JSONObject().apply {
            put("method", "ms.channel.emit")
            put("params", JSONObject().apply {
                put("event", "ed.apps.launch")
                put("to", "host")
                put("data", JSONObject().apply {
                    put("appId", appId)
                    put("action_type", "DEEP_LINK")
                    put("payloadJson", JSONObject().apply {
                        put("query", query)
                        put("v", query)
                    }.toString())
                })
            })
        }
        webSocket?.send(envelope.toString())
        Result.success(Unit)
    }

    override suspend fun sendKeySequence(text: String): Result<Unit> = withContext(Dispatchers.IO) {
        val base64Text = Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP)
        val envelope = JSONObject().apply {
            put("method", "ms.remote.control")
            put("params", JSONObject().apply {
                put("Cmd", base64Text)
                put("DataOfCmd", "base64")
                put("TypeOfRemote", "SendInputString")
            })
        }
        webSocket?.send(envelope.toString())
        Result.success(Unit)
    }
}

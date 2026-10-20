package org.omni.remote.driver

import org.omni.remote.core.model.RemoteCommand

interface TvDriver {
    suspend fun connect(ipAddress: String, token: String? = null): Result<String?>
    suspend fun disconnect()
    suspend fun isConnected(): Boolean
    suspend fun sendCommand(command: RemoteCommand): Result<Unit>
    suspend fun launchAppAndSearch(app: SupportedApp, query: String): Result<Unit>
    suspend fun sendKeySequence(text: String): Result<Unit>
}

enum class SupportedApp {
    PRIME_VIDEO, YOUTUBE, NETFLIX, SPOTIFY
}

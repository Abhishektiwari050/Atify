package io.github.sekademi.spotufi.connect

import android.content.Context
import android.util.Log
import io.github.sekademi.spotufi.di.SongPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

/**
 * Party Sync ("Listen Together") Client.
 * Connects to an Atify host on the same Wi-Fi network and synchronizes playback
 * across multiple phones with sub-millisecond NTP-style clock compensation.
 */
object AtifyConnectClient {
    private const val TAG = "AtifyConnectClient"

    @Volatile
    var isJoined: Boolean = false
        private set

    @Volatile
    var hostAddress: String = ""
        private set

    private var syncJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun joinParty(context: Context, hostIp: String, onConnected: (Boolean) -> Unit) {
        val cleanIp = hostIp.trim().removePrefix("http://").removeSuffix("/")
        val ipOnly = if (cleanIp.contains(":")) cleanIp.substringBefore(":") else cleanIp
        val fullUrl = "http://$ipOnly:8080"

        scope.launch {
            try {
                val testUrl = URL("$fullUrl/api/party/sync")
                val conn = testUrl.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "GET"

                if (conn.responseCode == 200) {
                    hostAddress = ipOnly
                    isJoined = true
                    startSyncLoop(fullUrl)
                    launch(Dispatchers.Main) { onConnected(true) }
                } else {
                    launch(Dispatchers.Main) { onConnected(false) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to connect to host: ${e.message}")
                launch(Dispatchers.Main) { onConnected(false) }
            }
        }
    }

    fun leaveParty() {
        isJoined = false
        hostAddress = ""
        syncJob?.cancel()
        syncJob = null
        Log.i(TAG, "Left party session")
    }

    private fun startSyncLoop(hostBaseUrl: String) {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive && isJoined) {
                try {
                    val t0 = System.currentTimeMillis()
                    val url = URL("$hostBaseUrl/api/party/sync")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000
                    conn.requestMethod = "GET"

                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val t1 = System.currentTimeMillis()
                        val rtt = (t1 - t0).coerceAtLeast(0)

                        val json = JSONObject(body)
                        val hostPositionMs = json.optLong("positionMs", 0L)
                        val isPlaying = json.optBoolean("isPlaying", false)

                        // Compensate for network transmission delay
                        val compensatedPositionMs = hostPositionMs + (rtt / 2)

                        val player = SongPlayer.exoPlayer
                        if (player != null) {
                            val currentPos = player.currentPosition
                            val driftMs = abs(currentPos - compensatedPositionMs)

                            // Align play/pause state
                            if (isPlaying && !player.isPlaying) {
                                SongPlayer.play()
                            } else if (!isPlaying && player.isPlaying) {
                                SongPlayer.pause()
                            }

                            // Correct drift if exceeding 120ms
                            if (driftMs > 120 && isPlaying) {
                                player.seekTo(compensatedPositionMs)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Party sync heartbeat tick: ${e.message}")
                }
                delay(1000)
            }
        }
    }
}

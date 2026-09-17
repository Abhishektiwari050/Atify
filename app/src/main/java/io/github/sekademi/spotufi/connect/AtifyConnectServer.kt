package io.github.sekademi.spotufi.connect

import android.content.Context
import android.util.Log
import io.github.sekademi.spotufi.di.CurrentSongState
import io.github.sekademi.spotufi.di.SongPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

/**
 * Embedded Atify Connect Server.
 * Enables zero-install local Wi-Fi browser control and multi-phone Party Sync.
 */
object AtifyConnectServer {
    private const val TAG = "AtifyConnectServer"
    const val PORT = 8080

    @Volatile
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    var currentSongState: CurrentSongState? = null
    var onActionNext: (() -> Unit)? = null
    var onActionPrev: (() -> Unit)? = null

    val isRunning: Boolean
        get() = serverSocket != null && serverSocket?.isClosed == false

    fun start(context: Context) {
        if (isRunning) return
        serverJob = scope.launch {
            try {
                val server = ServerSocket(PORT)
                serverSocket = server
                Log.i(TAG, "Atify Connect Server running at http://${getLocalIpAddress()}:$PORT")

                while (isActive && !server.isClosed) {
                    val client = server.accept()
                    launch { handleClient(client) }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Server error: ${e.message}")
                }
            }
        }
    }

    fun stop() {
        try {
            serverJob?.cancel()
            serverSocket?.close()
            serverSocket = null
            Log.i(TAG, "Atify Connect Server stopped")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping server: ${e.message}")
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val out = s.getOutputStream()

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0]
                val path = parts[1]

                // Read headers
                var contentLength = 0
                var line = reader.readLine()
                while (!line.isNullOrBlank()) {
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                    line = reader.readLine()
                }

                when {
                    method == "GET" && path == "/" -> {
                        sendResponse(out, 200, "text/html", WebRemoteAssets.HTML.toByteArray())
                    }
                    method == "GET" && path == "/api/state" -> {
                        val json = buildStateJson()
                        sendResponse(out, 200, "application/json", json.toByteArray())
                    }
                    method == "GET" && path == "/api/party/sync" -> {
                        val json = JSONObject().apply {
                            put("hostTime", System.currentTimeMillis())
                            put("positionMs", SongPlayer.exoPlayer?.currentPosition ?: 0L)
                            put("isPlaying", SongPlayer.exoPlayer?.isPlaying ?: false)
                            put("durationMs", SongPlayer.exoPlayer?.duration ?: 0L)
                        }.toString()
                        sendResponse(out, 200, "application/json", json.toByteArray())
                    }
                    method == "POST" && path == "/api/action" -> {
                        val body = CharArray(contentLength)
                        reader.read(body, 0, contentLength)
                        val bodyStr = String(body)
                        handleAction(bodyStr)
                        sendResponse(out, 200, "application/json", """{"status":"ok"}""".toByteArray())
                    }
                    else -> {
                        sendResponse(out, 404, "text/plain", "Not Found".toByteArray())
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Client socket handled: ${e.message}")
        }
    }

    private fun handleAction(body: String) {
        try {
            val json = JSONObject(body)
            val action = json.optString("action")
            val value = json.optLong("value", 0L)

            when (action) {
                "toggle_play" -> {
                    val player = SongPlayer.exoPlayer
                    if (player?.isPlaying == true) {
                        SongPlayer.pause()
                    } else {
                        SongPlayer.play()
                    }
                }
                "play" -> SongPlayer.play()
                "pause" -> SongPlayer.pause()
                "next" -> onActionNext?.invoke() ?: SongPlayer.exoPlayer?.seekToNextMediaItem()
                "prev" -> onActionPrev?.invoke() ?: SongPlayer.exoPlayer?.seekToPreviousMediaItem()
                "seek" -> SongPlayer.exoPlayer?.seekTo(value)
                "volume" -> {
                    val volFloat = (value.toFloat() / 100f).coerceIn(0f, 1f)
                    SongPlayer.exoPlayer?.volume = volFloat
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse action: ${e.message}")
        }
    }

    private fun buildStateJson(): String {
        val player = SongPlayer.exoPlayer
        val songState = currentSongState
        val isPlaying = player?.isPlaying ?: false
        val durationMs = player?.duration?.coerceAtLeast(0L) ?: 0L
        val positionMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val volume = ((player?.volume ?: 1f) * 100).toInt()

        val title = songState?.title?.value ?: ""
        val artist = songState?.singer?.value ?: ""
        val cover = songState?.coverUri?.value ?: ""

        val json = JSONObject().apply {
            put("isPlaying", isPlaying)
            put("title", title)
            put("artist", artist)
            put("coverUri", cover)
            put("durationMs", durationMs)
            put("positionMs", positionMs)
            put("volume", volume)
        }
        return json.toString()
    }

    private fun sendResponse(out: OutputStream, code: Int, contentType: String, data: ByteArray) {
        val statusText = if (code == 200) "OK" else "Not Found"
        val header = "HTTP/1.1 $code $statusText\r\n" +
                "Content-Type: $contentType; charset=UTF-8\r\n" +
                "Content-Length: ${data.size}\r\n" +
                "Connection: close\r\n" +
                "Access-Control-Allow-Origin: *\r\n\r\n"
        out.write(header.toByteArray())
        out.write(data)
        out.flush()
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine local IP: ${e.message}")
        }
        return "127.0.0.1"
    }
}

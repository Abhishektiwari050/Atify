package io.github.sekademi.spotufi.connect

import android.content.Context
import android.util.Log
import io.github.sekademi.spotufi.data.entity.SongsModel
import io.github.sekademi.spotufi.di.CurrentSongState
import io.github.sekademi.spotufi.di.SongPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Global Internet Party Manager (Listen Together / Jam).
 *
 * Allows users anywhere on Earth (over cellular mobile data 4G/5G or separate Wi-Fi networks)
 * to listen in perfect synchronization using a simple 6-digit Party Code without port-forwarding,
 * router setup, or shared LAN requirements.
 */
object AtifyPartyManager {
    private const val TAG = "AtifyPartyManager"
    private const val RELAY_BASE = "https://ntfy.sh/atify-jam-"

    enum class PartyRole {
        NONE,
        HOST,
        LISTENER,
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite read for real-time SSE / long-polling streams
        .build()

    private val shortHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO)

    var currentSongState: CurrentSongState? = null

    private val _partyRole = MutableStateFlow(PartyRole.NONE)
    val partyRole: StateFlow<PartyRole> = _partyRole.asStateFlow()

    private val _partyCode = MutableStateFlow("")
    val partyCode: StateFlow<String> = _partyCode.asStateFlow()

    private val _formattedCode = MutableStateFlow("")
    val formattedCode: StateFlow<String> = _formattedCode.asStateFlow()

    private val _statusMessage = MutableStateFlow("Offline")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _syncedTitle = MutableStateFlow("")
    val syncedTitle: StateFlow<String> = _syncedTitle.asStateFlow()

    private val _syncedSinger = MutableStateFlow("")
    val syncedSinger: StateFlow<String> = _syncedSinger.asStateFlow()

    private val _syncedCoverUri = MutableStateFlow("")
    val syncedCoverUri: StateFlow<String> = _syncedCoverUri.asStateFlow()

    private var hostJob: Job? = null
    private var listenerJob: Job? = null

    val isHosting: Boolean get() = _partyRole.value == PartyRole.HOST
    val isJoined: Boolean get() = _partyRole.value == PartyRole.LISTENER

    fun formatPartyCode(rawCode: String): String {
        val clean = rawCode.filter { it.isLetterOrDigit() }.uppercase()
        return if (clean.length == 6) {
            "${clean.substring(0, 3)}-${clean.substring(3)}"
        } else {
            clean
        }
    }

    /**
     * Start hosting an online party session with an auto-generated 6-digit party code.
     */
    fun createParty(context: Context): String {
        leaveOrEndParty()

        val rawCode = (100000..999999).random().toString()
        _partyRole.value = PartyRole.HOST
        _partyCode.value = rawCode
        _formattedCode.value = formatPartyCode(rawCode)
        _statusMessage.value = "Hosting party • Sharing playback live"

        Log.i(TAG, "Created party room $rawCode")

        // Immediately broadcast initial state
        broadcastCurrentState()

        // Continuous sync heartbeat loop (every 2.5 seconds)
        hostJob = scope.launch {
            while (isActive && _partyRole.value == PartyRole.HOST) {
                delay(2500)
                broadcastCurrentState()
            }
        }

        return _formattedCode.value
    }

    /**
     * Immediately publishes the current playback state to the active room.
     */
    fun broadcastCurrentState() {
        if (_partyRole.value != PartyRole.HOST) return
        val code = _partyCode.value
        if (code.isBlank()) return

        val state = currentSongState ?: return
        val title = state.title.value
        val singer = state.singer.value
        val album = state.album.value
        val coverUri = state.coverUri.value
        val songId = state.songId.value

        val queue = state.queue.value
        val activeSong = queue.firstOrNull { it.id == songId }
        val url = activeSong?.url.orEmpty()

        if (title.isBlank() || url.isBlank()) return

        val player = SongPlayer.exoPlayer
        val isPlaying = player?.isPlaying == true
        val positionMs = player?.currentPosition ?: 0L

        scope.launch {
            try {
                val payload = JSONObject().apply {
                    put("type", "sync")
                    put("code", code)
                    put("title", title)
                    put("singer", singer)
                    put("album", album)
                    put("coverUri", coverUri)
                    put("url", url)
                    put("songId", songId)
                    put("positionMs", positionMs)
                    put("isPlaying", isPlaying)
                    put("timestamp", System.currentTimeMillis())
                }

                val body = payload.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("$RELAY_BASE$code")
                    .post(body)
                    .build()

                shortHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "Failed to broadcast party state: HTTP ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Party state broadcast tick: ${e.message}")
            }
        }
    }

    /**
     * Join an online party session via a 6-digit party code.
     */
    fun joinParty(
        context: Context,
        inputCode: String,
        onResult: (Boolean, String?) -> Unit,
    ) {
        leaveOrEndParty()

        val cleanCode = inputCode.filter { it.isLetterOrDigit() }.uppercase()
        if (cleanCode.length < 4) {
            onResult(false, "Please enter a valid 6-digit Party Code.")
            return
        }

        _partyRole.value = PartyRole.LISTENER
        _partyCode.value = cleanCode
        _formattedCode.value = formatPartyCode(cleanCode)
        _statusMessage.value = "Connecting to party ${formatPartyCode(cleanCode)}…"

        // Launch listener routine
        listenerJob = scope.launch {
            try {
                // 1. Initial poll to verify room connectivity and fetch current state
                val checkReq = Request.Builder()
                    .url("$RELAY_BASE$cleanCode/json?poll=1")
                    .get()
                    .build()

                val pollSuccessful = try {
                    shortHttpClient.newCall(checkReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val bodyText = resp.body.string()
                            val lines = bodyText.lines().filter { it.isNotBlank() }
                            for (line in lines.reversed()) {
                                if (processPacket(context, line)) break
                            }
                            true
                        } else {
                            false
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Initial poll failed: ${e.message}")
                    false
                }

                if (!isActive) return@launch

                launch(Dispatchers.Main) {
                    _statusMessage.value = "Connected • Listening Together"
                    onResult(true, null)
                }

                // 2. Persistent streaming listener loop
                while (isActive && _partyRole.value == PartyRole.LISTENER) {
                    try {
                        val streamReq = Request.Builder()
                            .url("$RELAY_BASE$cleanCode/json")
                            .get()
                            .build()

                        httpClient.newCall(streamReq).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val stream = resp.body.byteStream()
                                val reader = BufferedReader(InputStreamReader(stream))
                                while (isActive && _partyRole.value == PartyRole.LISTENER) {
                                    val line = reader.readLine() ?: break
                                    if (line.isNotBlank()) {
                                        processPacket(context, line)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (isActive && _partyRole.value == PartyRole.LISTENER) {
                            Log.d(TAG, "Party stream reconnecting: ${e.message}")
                            delay(1500)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Party join exception: ${e.message}", e)
                if (isActive) {
                    launch(Dispatchers.Main) {
                        _statusMessage.value = "Connection failed"
                        onResult(false, e.message ?: "Failed to connect to Party room.")
                    }
                }
            }
        }
    }

    /**
     * Parses an incoming raw JSON line from ntfy.sh and applies playback state synchronization.
     */
    private fun processPacket(context: Context, rawLine: String): Boolean {
        try {
            val outerJson = JSONObject(rawLine)
            val eventType = outerJson.optString("event")
            if (eventType != "message") return false

            val messageStr = outerJson.optString("message")
            if (messageStr.isBlank()) return false

            val packet = JSONObject(messageStr)
            val type = packet.optString("type")

            if (type == "ended") {
                scope.launch(Dispatchers.Main) {
                    _statusMessage.value = "Host ended the party session"
                    leaveOrEndParty()
                }
                return true
            }

            if (type == "sync") {
                val title = packet.optString("title")
                val singer = packet.optString("singer")
                val album = packet.optString("album", "Party Session")
                val coverUri = packet.optString("coverUri")
                val url = packet.optString("url")
                val songId = packet.optInt("songId")
                val positionMs = packet.optLong("positionMs")
                val isPlaying = packet.optBoolean("isPlaying")
                val timestamp = packet.optLong("timestamp")

                if (title.isBlank() || url.isBlank()) return false

                _syncedTitle.value = title
                _syncedSinger.value = singer
                _syncedCoverUri.value = coverUri

                // Calculate transmission latency and compensate position
                val latency = (System.currentTimeMillis() - timestamp).coerceIn(0L, 10_000L)
                val compensatedPos = positionMs + (if (isPlaying) latency else 0L)

                scope.launch(Dispatchers.Main) {
                    val state = currentSongState ?: return@launch
                    val currentTitle = state.title.value
                    val player = SongPlayer.exoPlayer

                    // A. Track Change: Host jumped to a new song
                    if (currentTitle != title) {
                        val newSong = SongsModel(
                            id = if (songId != 0) songId else ("jam:${title}|$singer").hashCode() and 0x7fffffff,
                            title = title,
                            album = album,
                            singer = singer,
                            coverUri = coverUri,
                            url = url,
                        )
                        state.updateQueue(listOf(newSong))
                        state.updateSongState(
                            newSong.coverUri,
                            newSong.title,
                            newSong.singer,
                            isPlaying,
                            newSong.id,
                            0,
                            newSong.album,
                        )
                        SongPlayer.playSong(url, context)
                        if (compensatedPos > 1200) {
                            SongPlayer.exoPlayer?.seekTo(compensatedPos)
                        }
                        if (!isPlaying) {
                            SongPlayer.pause()
                        }
                    } else if (player != null) {
                        // B. Same Track: Align play/pause state
                        val localPlaying = player.isPlaying
                        if (isPlaying && !localPlaying) {
                            SongPlayer.play()
                        } else if (!isPlaying && localPlaying) {
                            SongPlayer.pause()
                        }

                        // Align time drift if discrepancy exceeds 350ms
                        val currentPos = player.currentPosition
                        val drift = abs(currentPos - compensatedPos)
                        if (drift > 350 && isPlaying) {
                            player.seekTo(compensatedPos)
                        }
                    }
                }
                return true
            }
        } catch (e: Exception) {
            Log.d(TAG, "Ignored malformed party packet: ${e.message}")
        }
        return false
    }

    /**
     * Leave the current party or end hosting.
     */
    fun leaveOrEndParty() {
        val wasHost = _partyRole.value == PartyRole.HOST
        val code = _partyCode.value

        if (wasHost && code.isNotBlank()) {
            scope.launch {
                try {
                    val endedPayload = JSONObject().apply {
                        put("type", "ended")
                        put("code", code)
                    }
                    val body = endedPayload.toString().toRequestBody("application/json".toMediaType())
                    val req = Request.Builder()
                        .url("$RELAY_BASE$code")
                        .post(body)
                        .build()
                    shortHttpClient.newCall(req).execute().close()
                } catch (_: Exception) {}
            }
        }

        hostJob?.cancel()
        hostJob = null
        listenerJob?.cancel()
        listenerJob = null

        _partyRole.value = PartyRole.NONE
        _partyCode.value = ""
        _formattedCode.value = ""
        _statusMessage.value = "Offline"
        _syncedTitle.value = ""
        _syncedSinger.value = ""
        _syncedCoverUri.value = ""

        Log.i(TAG, "Left party session")
    }
}

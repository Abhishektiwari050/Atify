package io.github.sekademi.spotufi.ui.notification

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.sekademi.spotufi.data.api.Api
import io.github.sekademi.spotufi.data.api.Response
import io.github.sekademi.spotufi.data.api.SpotifySync
import io.github.sekademi.spotufi.data.entity.SongsModel
import io.github.sekademi.spotufi.di.CurrentSongState
import io.github.sekademi.spotufi.di.RepeatMode
import io.github.sekademi.spotufi.di.SongPlayer
import io.github.sekademi.spotufi.di.SpotifyWebPlayer
import io.github.sekademi.spotufi.ui.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Standard Android [MediaBrowserServiceCompat] implementation that exposes Atify's
 * library (Liked Songs, Downloads, Playlists, Albums, and Search) to AI voice assistants
 * (Google Assistant / Gemini), Android Auto, Wear OS, and system media controllers.
 */
@AndroidEntryPoint
class MusicBrowserService : MediaBrowserServiceCompat() {

    @Inject lateinit var currentSongState: CurrentSongState
    @Inject lateinit var repository: AppRepository

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val trackById = ConcurrentHashMap<String, SongsModel>()
    private val queueByTrackId = ConcurrentHashMap<String, List<SongsModel>>()

    companion object {
        const val MEDIA_ROOT_ID = "root"
        const val NODE_LIKED = "liked"
        const val NODE_DOWNLOADS = "downloads"
        const val NODE_PLAYLISTS = "playlists"
        const val NODE_ALBUMS = "albums"
    }

    override fun onCreate() {
        super.onCreate()
        SongPlayer.ensureCreated(this)
        val session = MediaSessionHolder.getOrCreateSession(this, mediaSessionCallback)
        sessionToken = session.sessionToken
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        val extras = Bundle().apply {
            putBoolean(BrowserRoot.EXTRA_RECENT, true)
            putBoolean(BrowserRoot.EXTRA_OFFLINE, true)
            putBoolean(BrowserRoot.EXTRA_SUGGESTED, true)
            putInt("android.media.browse.SEARCH_SUPPORTED", 1)
        }
        return BrowserRoot(MEDIA_ROOT_ID, extras)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaItem>>
    ) {
        result.detach()
        serviceScope.launch(Dispatchers.IO) {
            val items = loadChildrenForParent(parentId)
            result.sendResult(items)
        }
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<List<MediaItem>>
    ) {
        result.detach()
        serviceScope.launch(Dispatchers.IO) {
            val clean = sanitizeVoiceQuery(query).ifBlank { query }
            val songs = lastSuccess(repository.searchSongs(clean)).orEmpty()
            val items = registerAndConvertSongs("search/$clean", songs)
            result.sendResult(items)
        }
    }

    private suspend fun loadChildrenForParent(parentId: String): List<MediaItem> = when {
        parentId == MEDIA_ROOT_ID -> listOf(
            createFolderItem(NODE_LIKED, "Liked Songs", "Saved songs in your library"),
            createFolderItem(NODE_DOWNLOADS, "Downloads", "Offline downloaded songs"),
            createFolderItem(NODE_PLAYLISTS, "Playlists", "Saved and custom playlists"),
            createFolderItem(NODE_ALBUMS, "Albums", "Saved albums"),
        )
        parentId == NODE_LIKED -> {
            val liked = io.github.sekademi.spotufi.data.preferences.getLocalLikedSongs(applicationContext).ifEmpty {
                lastSuccess(repository.provideLikedSongs()).orEmpty()
            }
            registerAndConvertSongs(NODE_LIKED, liked)
        }
        parentId == NODE_DOWNLOADS -> {
            val downloaded = io.github.sekademi.spotufi.data.preferences.getDownloadedSongs(applicationContext)
            registerAndConvertSongs(NODE_DOWNLOADS, downloaded)
        }
        parentId == NODE_PLAYLISTS -> {
            val library = lastSuccess(repository.provideLibrary()).orEmpty()
            library.filter { it.isPlaylist && it.spotifyId != Api.LIKED_SONGS_ID && it.spotifyId != Api.DOWNLOADS_ID }
                .map { createFolderItem("playlist/${it.spotifyId}", it.name, it.subtitle.ifBlank { "Playlist" }, it.coverUri) }
        }
        parentId == NODE_ALBUMS -> {
            val library = lastSuccess(repository.provideLibrary()).orEmpty()
            library.filter { !it.isPlaylist }
                .map {
                    createFolderItem(
                        "album/${android.net.Uri.encode(it.name)}/${android.net.Uri.encode(it.artists)}",
                        it.name,
                        it.artists,
                        it.coverUri,
                    )
                }
        }
        parentId.startsWith("playlist/") -> {
            val playlistId = parentId.removePrefix("playlist/")
            val songs = lastSuccess(repository.providePlaylistSongs(playlistId)).orEmpty()
            registerAndConvertSongs(parentId, songs)
        }
        parentId.startsWith("album/") -> {
            val parts = parentId.removePrefix("album/").split('/')
            val name = android.net.Uri.decode(parts.getOrElse(0) { "" })
            val artist = android.net.Uri.decode(parts.getOrElse(1) { "" })
            val songs = lastSuccess(repository.provideAlbumSongs(name, artist)).orEmpty()
            registerAndConvertSongs(parentId, songs)
        }
        else -> emptyList()
    }

    private fun registerAndConvertSongs(parentId: String, songs: List<SongsModel>): List<MediaItem> {
        songs.forEach { song ->
            trackById["song/${song.id}"] = song
            queueByTrackId["song/${song.id}"] = songs
        }
        return songs.map { song ->
            val description = MediaDescriptionCompat.Builder()
                .setMediaId("song/${song.id}")
                .setTitle(song.title)
                .setSubtitle(song.singer)
                .setDescription(song.album)
                .apply {
                    if (song.coverUri.isNotBlank()) {
                        setIconUri(android.net.Uri.parse(song.coverUri))
                    }
                    if (song.url.isNotBlank()) {
                        setMediaUri(android.net.Uri.parse(song.url))
                    }
                }
                .build()
            MediaItem(description, MediaItem.FLAG_PLAYABLE)
        }
    }

    private fun createFolderItem(id: String, title: String, subtitle: String = "", coverUri: String = ""): MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .apply {
                if (coverUri.isNotBlank()) {
                    setIconUri(android.net.Uri.parse(coverUri))
                }
            }
            .build()
        return MediaItem(description, MediaItem.FLAG_BROWSABLE)
    }

    private suspend fun <T> lastSuccess(flow: Flow<Response<T>>): T? =
        runCatching { flow.toList() }.getOrNull()
            ?.filterIsInstance<Response.Success<T>>()
            ?.lastOrNull()?.data

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            startPlaybackService()
            val isWeb = SongPlayer.webPlaybackActive()
            if (isWeb) {
                SpotifyWebPlayer.resume()
                currentSongState.updatePlayingState(true)
            } else {
                SongPlayer.play()
                currentSongState.updatePlayingState(true)
            }
            MediaSessionHolder.updatePlaybackState(
                PlaybackStateCompat.STATE_PLAYING,
                repeatMode = currentSongState.repeat.value,
                isShuffle = currentSongState.shuffle.value
            )
        }

        override fun onPause() {
            SongPlayer.pause()
            currentSongState.updatePlayingState(false)
            MediaSessionHolder.updatePlaybackState(
                PlaybackStateCompat.STATE_PAUSED,
                repeatMode = currentSongState.repeat.value,
                isShuffle = currentSongState.shuffle.value
            )
        }

        override fun onSkipToNext() {
            advance(forward = true)
        }

        override fun onSkipToPrevious() {
            advance(forward = false)
        }

        override fun onSeekTo(pos: Long) {
            SongPlayer.seekTo(pos)
            val isPlaying = SongPlayer.exoPlayer?.isPlaying == true || SongPlayer.webPlaybackActive()
            MediaSessionHolder.updatePlaybackState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                position = pos,
                repeatMode = currentSongState.repeat.value,
                isShuffle = currentSongState.shuffle.value
            )
        }

        override fun onStop() {
            SongPlayer.pause()
            currentSongState.updatePlayingState(false)
            MediaSessionHolder.updatePlaybackState(
                PlaybackStateCompat.STATE_STOPPED,
                repeatMode = currentSongState.repeat.value,
                isShuffle = currentSongState.shuffle.value
            )
        }

        override fun onSetRepeatMode(repeatMode: Int) {
            when (repeatMode) {
                PlaybackStateCompat.REPEAT_MODE_NONE -> currentSongState.updateRepeatState(RepeatMode.OFF)
                PlaybackStateCompat.REPEAT_MODE_ONE -> currentSongState.updateRepeatState(RepeatMode.ONE)
                PlaybackStateCompat.REPEAT_MODE_ALL,
                PlaybackStateCompat.REPEAT_MODE_GROUP -> currentSongState.updateRepeatState(RepeatMode.ALL)
            }
        }

        override fun onSetShuffleMode(shuffleMode: Int) {
            val enabled = shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_NONE
            currentSongState.updateShuffleState(enabled)
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            startPlaybackService()
            handleVoiceQuery(query.orEmpty())
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            if (mediaId.isNullOrBlank()) return
            startPlaybackService()
            val track = trackById[mediaId] ?: currentSongState.queue.value.find { "song/${it.id}" == mediaId || it.id.toString() == mediaId || it.spotifyTrackId == mediaId }
            if (track != null) {
                val customQueue = queueByTrackId[mediaId] ?: currentSongState.queue.value
                val queueToUse = if (customQueue.isNotEmpty()) customQueue else listOf(track)
                currentSongState.updateQueue(queueToUse)
                val idx = queueToUse.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                currentSongState.updateSongState(track.coverUri, track.title, track.singer, true, track.id, idx, track.album)
                SongPlayer.playSong(track.url, applicationContext)
                MediaSessionHolder.updatePlaybackState(
                    PlaybackStateCompat.STATE_PLAYING,
                    repeatMode = currentSongState.repeat.value,
                    isShuffle = currentSongState.shuffle.value
                )
            } else {
                handleVoiceQuery(mediaId)
            }
        }

        override fun onPlayFromUri(uri: android.net.Uri?, extras: Bundle?) {
            startPlaybackService()
            uri?.toString()?.let { handleVoiceQuery(it) }
        }

        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
            return super.onMediaButtonEvent(mediaButtonEvent)
        }
    }

    private fun startPlaybackService() {
        try {
            val serviceIntent = Intent(applicationContext, PlaybackService::class.java)
            ContextCompat.startForegroundService(applicationContext, serviceIntent)
        } catch (_: Exception) {}
    }

    private fun advance(forward: Boolean) {
        val queue = currentSongState.queue.value
        if (queue.isEmpty()) return
        val curId = currentSongState.songId.value
        val cur = queue.indexOfFirst { it.id == curId }
            .let { if (it >= 0) it else currentSongState.songIndex.value }
            .coerceIn(0, queue.size - 1)
        val nextIdx = if (forward) {
            if (cur < queue.size - 1) cur + 1 else 0
        } else {
            if (cur > 0) cur - 1 else queue.size - 1
        }
        val song = queue[nextIdx]
        currentSongState.updateSongState(
            song.coverUri, song.title, song.singer, true,
            song.id, nextIdx, song.album
        )
        SongPlayer.playSong(song.url, applicationContext)
        MediaSessionHolder.updatePlaybackState(
            PlaybackStateCompat.STATE_PLAYING,
            repeatMode = currentSongState.repeat.value,
            isShuffle = currentSongState.shuffle.value
        )
    }

    private fun handleVoiceQuery(query: String) {
        serviceScope.launch(Dispatchers.IO) {
            val cleanQuery = sanitizeVoiceQuery(query)
            if (cleanQuery.isBlank()) {
                withContext(Dispatchers.Main) {
                    val currentQueue = currentSongState.queue.value
                    if (currentQueue.isNotEmpty()) {
                        if (SongPlayer.getCurrentPosition() > 0) {
                            SongPlayer.play()
                        } else {
                            val cur = currentQueue.getOrNull(currentSongState.songIndex.value) ?: currentQueue.first()
                            SongPlayer.playSong(cur.url, applicationContext)
                        }
                    } else {
                        val liked = io.github.sekademi.spotufi.data.preferences.getLocalLikedSongs(applicationContext)
                        if (liked.isNotEmpty()) {
                            currentSongState.updateQueue(liked)
                            val first = liked.first()
                            currentSongState.updateSongState(first.coverUri, first.title, first.singer, true, first.id, 0, first.album)
                            SongPlayer.playSong(first.url, applicationContext)
                        } else {
                            val fallbackHits = lastSuccess(repository.searchSongs("top hits")) ?: emptyList()
                            if (fallbackHits.isNotEmpty()) {
                                currentSongState.updateQueue(fallbackHits)
                                val first = fallbackHits.first()
                                currentSongState.updateSongState(first.coverUri, first.title, first.singer, true, first.id, 0, first.album)
                                SongPlayer.playSong(first.url, applicationContext)
                            }
                        }
                    }
                    MediaSessionHolder.updatePlaybackState(
                        PlaybackStateCompat.STATE_PLAYING,
                        repeatMode = currentSongState.repeat.value,
                        isShuffle = currentSongState.shuffle.value
                    )
                }
                return@launch
            }

            val songs = lastSuccess(repository.searchSongs(cleanQuery)).orEmpty()
            withContext(Dispatchers.Main) {
                if (songs.isNotEmpty()) {
                    currentSongState.updateQueue(songs)
                    val first = songs.first()
                    currentSongState.updateSongState(first.coverUri, first.title, first.singer, true, first.id, 0, first.album)
                    SongPlayer.playSong(first.url, applicationContext)
                    MediaSessionHolder.updatePlaybackState(
                        PlaybackStateCompat.STATE_PLAYING,
                        repeatMode = currentSongState.repeat.value,
                        isShuffle = currentSongState.shuffle.value
                    )
                }
            }
        }
    }

    private fun sanitizeVoiceQuery(rawQuery: String): String {
        var q = rawQuery.trim()
        if (q.isBlank()) return ""
        q = q.replace(Regex("""^(?i)(play|listen\s+to|start|stream|put\s+on)\s+"""), "").trim()
        q = q.replace(Regex("""(?i)\s+(on|using|with|in|via|from|by)\s+(atify|spotufi|85|eighty[\s-]?five|at\s*if\s*i|addify|atifly|80[\s-]?5)\b.*$"""), "").trim()
        q = q.replace(Regex("""(?i)\s+\b(atify|spotufi|85|eighty[\s-]?five|at\s*if\s*i|addify|atifly|80[\s-]?5)\b.*$"""), "").trim()
        q = q.replace(Regex("""^(?i)(atify|spotufi|85|eighty[\s-]?five|at\s*if\s*i|addify|atifly|80[\s-]?5)\s+"""), "").trim()
        return q
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }
}

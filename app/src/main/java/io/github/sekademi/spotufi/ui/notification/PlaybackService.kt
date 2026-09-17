package io.github.sekademi.spotufi.ui.notification

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.lifecycle.lifecycleScope
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.github.sekademi.spotufi.MainActivity
import io.github.sekademi.spotufi.R
import androidx.compose.runtime.snapshotFlow
import io.github.sekademi.spotufi.data.api.Api
import io.github.sekademi.spotufi.data.api.Response
import io.github.sekademi.spotufi.data.api.SpotifySync
import io.github.sekademi.spotufi.data.entity.SongsModel
import io.github.sekademi.spotufi.di.CurrentSongState
import io.github.sekademi.spotufi.di.RepeatMode
import io.github.sekademi.spotufi.di.SongPlayer
import io.github.sekademi.spotufi.di.SpotifyWebPlayer
import io.github.sekademi.spotufi.ui.repository.AppRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Hosts a [MediaSession] over the app's single ExoPlayer (owned by [SongPlayer]).
 * This is what surfaces the track in the system notification center / lock screen
 * and routes the notification's transport controls (play/pause/seek/next/prev)
 * back into playback. Next/previous are wired to the in-app queue because our
 * player only ever holds one resolved stream at a time (YouTube URLs are resolved
 * lazily per track), so we advance the queue ourselves rather than via a playlist.
 *
 * It is a [MediaLibraryService] (not just a session service) so Android Auto can
 * browse the library — Liked Songs, Downloads, playlists and albums — and start
 * playback from the car.
 */
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var currentSongState: CurrentSongState
    @Inject lateinit var repository: AppRepository

    private var mediaSession: MediaLibrarySession? = null

    // Android Auto browse cache: mediaId → track, and mediaId → the list it was
    // browsed from (so playing a track queues its whole playlist/album).
    private val trackById = java.util.concurrent.ConcurrentHashMap<String, SongsModel>()
    private val queueByTrackId = java.util.concurrent.ConcurrentHashMap<String, List<SongsModel>>()
    private var webPlayer: WebMediaPlayer? = null
    private var showingWeb = false

    private val playerListener = object : Player.Listener {
        private var lastErrorSongId: Int? = null

        override fun onPlaybackStateChanged(playbackState: Int) {
            currentSongState.updateBufferingState(playbackState == Player.STATE_BUFFERING)
            if (playbackState == Player.STATE_READY) {
                lastErrorSongId = null
            }
            if (playbackState == Player.STATE_ENDED) {
                lastErrorSongId = null
                if (SongPlayer.isCrossfadeActive()) {
                    // Ignore the old player's STATE_ENDED event during an active crossfade.
                    // The crossfade routine itself handles the transition and promotes the new player.
                    return
                }
                val p = SongPlayer.exoPlayer
                if (p != null) {
                    val duration = p.duration
                    val position = p.currentPosition
                    if (duration <= 0 || position < duration - 6000) {
                        // Spurious STATE_ENDED event (e.g. player cleared/reset or ended prematurely before loading)! Ignore it!
                        return
                    }
                }
                val queue = currentSongState.queue.value
                val curId = currentSongState.songId.value
                val cur = queue.indexOfFirst { it.id == curId }
                    .let { if (it >= 0) it else currentSongState.songIndex.value }
                if (cur in queue.indices) {
                    val completed = queue[cur]
                    val artistList = completed.artistIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        .ifEmpty { completed.singer.split(",", "&", "/").map { it.trim() }.filter { it.isNotBlank() } }
                    if (artistList.isNotEmpty()) {
                        io.github.sekademi.spotufi.data.recommendation.TasteProfileEngine.recordCompletion(applicationContext, artistList)
                    }
                }
                when (currentSongState.repeat.value) {
                    RepeatMode.ONE -> {
                        val queue = currentSongState.queue.value
                        if (queue.isNotEmpty()) {
                            val curId = currentSongState.songId.value
                            val cur = queue.indexOfFirst { it.id == curId }
                                .let { if (it >= 0) it else currentSongState.songIndex.value }
                                .coerceIn(0, queue.size - 1)
                            val song = queue[cur]
                            SongPlayer.playSong(song.url, applicationContext)
                        } else {
                            SongPlayer.exoPlayer?.seekTo(0)
                            SongPlayer.exoPlayer?.play()
                        }
                    }
                    RepeatMode.ALL -> {
                        advance(forward = true)
                    }
                    RepeatMode.OFF -> {
                        val queue = currentSongState.queue.value
                        if (queue.isNotEmpty()) {
                            val curId = currentSongState.songId.value
                            val cur = queue.indexOfFirst { it.id == curId }
                                .let { if (it >= 0) it else currentSongState.songIndex.value }
                                .coerceIn(0, queue.size - 1)
                            if (cur < queue.size - 1 || io.github.sekademi.spotufi.data.preferences.isAutoplayEnabled(applicationContext)) {
                                advance(forward = true)
                            }
                        }
                    }
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            android.util.Log.e("PlaybackService", "Player error during playback: ${error.message}", error)
            val queue = currentSongState.queue.value
            val curId = currentSongState.songId.value
            val cur = queue.indexOfFirst { it.id == curId }
            if (cur >= 0) {
                val failedSong = queue[cur]
                SongPlayer.invalidateResolvedStream(failedSong.url)

                // First failure on this song: retry once with fresh stream resolution
                if (lastErrorSongId != curId) {
                    lastErrorSongId = curId
                    android.util.Log.w("PlaybackService", "Retrying with fresh stream resolution for: ${failedSong.title}")
                    SongPlayer.playSong(failedSong.url, applicationContext)
                    return
                }
            }
            lastErrorSongId = null
            advance(forward = true)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && mediaItem != null) {
                val nextId = mediaItem.mediaId.toIntOrNull()
                val queue = currentSongState.queue.value
                val nextSong = queue.firstOrNull { it.id == nextId }
                if (nextSong != null) {
                    val nextIdx = queue.indexOf(nextSong)
                    currentSongState.updateSongState(
                        nextSong.coverUri, nextSong.title, nextSong.singer,
                        true, nextSong.id, nextIdx, nextSong.album
                    )
                    // Proactively queue the subsequent track for uninterrupted gapless playback
                    if (nextIdx in 0 until queue.lastIndex) {
                        val subsequent = queue[nextIdx + 1]
                        SongPlayer.queueNextMediaItem(subsequent, applicationContext)
                    }
                    maybeExtendRadio(queue, nextIdx)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        SongPlayer.ensureCreated(this)

        // explicitly order notification buttons: [Like] [Playback Controls] [Close]
        val notificationProvider = object : DefaultMediaNotificationProvider(this) {
            override fun getMediaButtons(
                session: MediaSession,
                playerCommands: Player.Commands,
                customLayout: ImmutableList<CommandButton>,
                showPauseButton: Boolean
            ): ImmutableList<CommandButton> {
                val buttons = super.getMediaButtons(session, playerCommands, customLayout, showPauseButton)
                val likeBtn = buttons.find { it.sessionCommand?.customAction == "ACTION_TOGGLE_LIKE" }
                val closeBtn = buttons.find { it.sessionCommand?.customAction == "ACTION_CLOSE" }
                val coreButtons = buttons.filter { it != likeBtn && it != closeBtn }
                val builder = ImmutableList.builder<CommandButton>()
                if (likeBtn != null) builder.add(likeBtn)
                builder.addAll(coreButtons)
                if (closeBtn != null) builder.add(closeBtn)
                return builder.build()
            }
        }
        setMediaNotificationProvider(notificationProvider)

        lifecycleScope.launch {
            snapshotFlow { currentSongState.songId.value }.collect {
                mediaSession?.let { session -> updateSessionCustomLayout(session) }
                io.github.sekademi.spotufi.ui.widget.NowPlayingWidgetProvider.updateAllWidgets(
                    this@PlaybackService,
                    currentSongState.title.value,
                    currentSongState.singer.value,
                    currentSongState.playingState.value,
                    currentSongState.coverUri.value,
                )
            }
        }

        lifecycleScope.launch {
            snapshotFlow { currentSongState.playingState.value }.collect { isPlaying ->
                io.github.sekademi.spotufi.ui.widget.NowPlayingWidgetProvider.updateAllWidgets(
                    this@PlaybackService,
                    currentSongState.title.value,
                    currentSongState.singer.value,
                    isPlaying,
                    currentSongState.coverUri.value,
                )
            }
        }

        // Let the player advance the in-app queue itself during a crossfade.
        SongPlayer.initCrossfade(this, currentSongState)
        val base = SongPlayer.exoPlayer ?: return
        base.addListener(playerListener)

        // Tapping the notification opens the app (back on the Now Playing screen).
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        webPlayer = WebMediaPlayer(mainLooper, currentSongState) { forward -> advance(forward) }

        mediaSession = MediaLibrarySession.Builder(this, wrap(base), LibraryCallback())
            .setSessionActivity(sessionActivity)
            .build()

        // When a crossfade promotes a new ExoPlayer instance, re-bind the session to it
        // (runs on the main thread; setPlayer is the supported way to swap a session's player).
        SongPlayer.onPlayerSwapped = { newPlayer ->
            if (!showingWeb) mediaSession?.player = wrap(newPlayer)
            newPlayer.addListener(playerListener)
        }

        // As the hidden web player streams, keep the notification in sync and swap
        // the session between the web player (during web playback) and the ExoPlayer.
        SpotifyWebPlayer.onStateChanged = {
            syncSessionPlayer()
            if (showingWeb) {
                webPlayer?.refresh()
                // Reflect the web player's real play/pause state into the in-app UI
                // so the on-screen icon matches after the notification's pause.
                currentSongState.updatePlayingState(SpotifyWebPlayer.isPlaying)
            }
        }

        io.github.sekademi.spotufi.connect.AtifyConnectServer.currentSongState = currentSongState
        io.github.sekademi.spotufi.connect.AtifyConnectServer.onActionNext = { advance(forward = true) }
        io.github.sekademi.spotufi.connect.AtifyConnectServer.onActionPrev = { advance(forward = false) }
        if (io.github.sekademi.spotufi.data.preferences.isWebRemoteEnabled(this)) {
            io.github.sekademi.spotufi.connect.AtifyConnectServer.start(this)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            val query = intent.getStringExtra(android.app.SearchManager.QUERY)
                ?: intent.getStringExtra(android.provider.MediaStore.EXTRA_MEDIA_TITLE)
                ?: listOfNotNull(
                    intent.getStringExtra(android.provider.MediaStore.EXTRA_MEDIA_ARTIST),
                    intent.getStringExtra(android.provider.MediaStore.EXTRA_MEDIA_ALBUM),
                ).joinToString(" ").takeIf { it.isNotBlank() }
            if (!query.isNullOrBlank()) {
                handleVoiceQuery(query)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /** Point the media session at whichever engine is currently producing audio. */
    private fun syncSessionPlayer() {
        val wantWeb = SongPlayer.webPlaybackActive()
        if (wantWeb == showingWeb) return
        showingWeb = wantWeb
        val session = mediaSession ?: return
        session.player = if (wantWeb) {
            webPlayer ?: return
        } else {
            wrap(SongPlayer.exoPlayer ?: return)
        }
    }

    /** Wrap an ExoPlayer so the media session routes next/previous to our in-app queue
     *  (the player only ever holds one resolved stream at a time). */
    private fun wrap(base: Player): ForwardingPlayer = object : ForwardingPlayer(base) {
        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon()
                .add(COMMAND_SEEK_TO_NEXT)
                .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(COMMAND_SEEK_TO_PREVIOUS)
                .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(COMMAND_SEEK_BACK)
                .add(COMMAND_SEEK_FORWARD)
                .build()

        override fun isCommandAvailable(command: Int): Boolean = when (command) {
            COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            COMMAND_SEEK_BACK, COMMAND_SEEK_FORWARD -> true
            else -> super.isCommandAvailable(command)
        }

        override fun hasNextMediaItem() = true
        override fun hasPreviousMediaItem() = true
        override fun seekToNext() = advance(forward = true)
        override fun seekToNextMediaItem() = advance(forward = true)
        override fun seekToPrevious() = advance(forward = false)
        override fun seekToPreviousMediaItem() = advance(forward = false)
    }

    @Volatile private var radioLoading = false
    @Volatile private var autoplayTriggering = false

    private fun maybeExtendRadio(queueSongs: List<SongsModel>, cur: Int) {
        if (!io.github.sekademi.spotufi.data.preferences.isAutoplayEnabled(applicationContext)) return
        if (radioLoading || cur < queueSongs.size - 2) return
        val seeds = queueSongs.takeLast(5)
            .mapNotNull { it.spotifyTrackId.ifBlank { null } }
            .distinct()
        radioLoading = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val newTracks = mutableListOf<SongsModel>()
                if (seeds.isNotEmpty()) {
                    val recs = runCatching { repository.provideRecommendations(seeds) }.getOrNull()
                    if (!recs.isNullOrEmpty()) newTracks.addAll(recs)
                }
                if (newTracks.isEmpty()) {
                    val lastSong = queueSongs.lastOrNull()
                    if (lastSong != null) {
                        val query = "${lastSong.singer} ${lastSong.title}"
                        val searchRes = com.metrolist.innertube.YouTube.search(query, com.metrolist.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()
                        val items = searchRes?.items?.filterIsInstance<com.metrolist.innertube.models.SongItem>()
                        if (!items.isNullOrEmpty()) {
                            newTracks.addAll(items.take(10).map { songItem ->
                                SongsModel(
                                    id = songItem.id.hashCode(),
                                    title = songItem.title,
                                    singer = songItem.artists.joinToString(", ") { it.name },
                                    album = songItem.album?.name ?: "",
                                    coverUri = songItem.thumbnail,
                                    durationMs = (songItem.duration ?: 0) * 1000,
                                    spotifyTrackId = "",
                                    url = "https://music.youtube.com/watch?v=${songItem.id}",
                                )
                            })
                        }
                    }
                }
                val existing = currentSongState.queue.value
                val existingIds = existing.map { it.id }.toSet()
                val fresh = newTracks.filter { it.id !in existingIds }
                if (fresh.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        currentSongState.updateQueue(existing + fresh)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("PlaybackService", "Autoplay radio extension failed: ${e.message}")
            } finally {
                radioLoading = false
            }
        }
    }

    private fun triggerAutoplay(seedSong: SongsModel) {
        if (autoplayTriggering) return
        autoplayTriggering = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val newTracks = mutableListOf<SongsModel>()
                val seeds = currentSongState.queue.value.takeLast(5)
                    .mapNotNull { it.spotifyTrackId.ifBlank { null } }
                    .distinct()
                if (seeds.isNotEmpty()) {
                    val recs = runCatching { repository.provideRecommendations(seeds) }.getOrNull()
                    if (!recs.isNullOrEmpty()) newTracks.addAll(recs)
                }
                if (newTracks.isEmpty()) {
                    val query = "${seedSong.singer} ${seedSong.title}"
                    val searchRes = com.metrolist.innertube.YouTube.search(query, com.metrolist.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    val items = searchRes?.items?.filterIsInstance<com.metrolist.innertube.models.SongItem>()
                    if (!items.isNullOrEmpty()) {
                        newTracks.addAll(items.take(15).map { songItem ->
                            SongsModel(
                                id = songItem.id.hashCode(),
                                title = songItem.title,
                                singer = songItem.artists.joinToString(", ") { it.name },
                                album = songItem.album?.name ?: "",
                                coverUri = songItem.thumbnail,
                                durationMs = (songItem.duration ?: 0) * 1000,
                                spotifyTrackId = "",
                                url = "https://music.youtube.com/watch?v=${songItem.id}",
                            )
                        })
                    }
                }
                val existing = currentSongState.queue.value
                val existingIds = existing.map { it.id }.toSet()
                val fresh = newTracks.filter { it.id !in existingIds }
                withContext(Dispatchers.Main) {
                    if (fresh.isNotEmpty()) {
                        val newQueue = existing + fresh
                        currentSongState.updateQueue(newQueue)
                        val nextSong = fresh[0]
                        val newIdx = existing.size
                        currentSongState.updateSongState(
                            nextSong.coverUri, nextSong.title, nextSong.singer,
                            true, nextSong.id, newIdx, nextSong.album
                        )
                        SongPlayer.playSong(nextSong.url, applicationContext)
                    }
                    autoplayTriggering = false
                }
            } catch (e: Exception) {
                android.util.Log.e("PlaybackService", "Autoplay failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    autoplayTriggering = false
                }
            }
        }
    }

    /** Advance the in-app queue one step in the given direction and start it. */
    private fun advance(forward: Boolean) {
        val queue = currentSongState.queue.value
        if (queue.isEmpty()) return
        val curId = currentSongState.songId.value
        val cur = queue.indexOfFirst { it.id == curId }
            .let { if (it >= 0) it else currentSongState.songIndex.value }
            .coerceIn(0, queue.size - 1)
        
        val nextIdx: Int
        if (forward) {
            val p = SongPlayer.exoPlayer
            if (p != null && cur in queue.indices) {
                val pos = p.currentPosition
                val dur = p.duration
                if (dur > 40_000L && pos in 1L..25_000L) {
                    val skipped = queue[cur]
                    val artistList = skipped.artistIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        .ifEmpty { skipped.singer.split(",", "&", "/").map { it.trim() }.filter { it.isNotBlank() } }
                    if (artistList.isNotEmpty()) {
                        io.github.sekademi.spotufi.data.recommendation.TasteProfileEngine.recordSkip(applicationContext, artistList)
                    }
                }
            }
            maybeExtendRadio(queue, cur)
            if (cur < queue.size - 1) {
                nextIdx = cur + 1
            } else {
                if (currentSongState.repeat.value == RepeatMode.ALL) {
                    nextIdx = 0
                } else {
                    if (io.github.sekademi.spotufi.data.preferences.isAutoplayEnabled(applicationContext)) {
                        triggerAutoplay(queue[cur])
                    }
                    return
                }
            }
        } else {
            if (cur > 0) {
                nextIdx = cur - 1
            } else {
                if (currentSongState.repeat.value == RepeatMode.ALL) {
                    nextIdx = queue.size - 1
                } else {
                    return // do nothing at the beginning of the queue
                }
            }
        }
        val song = queue[nextIdx]
        currentSongState.updateSongState(
            song.coverUri, song.title, song.singer, true,
            song.id, nextIdx, song.album
        )
        SongPlayer.playSong(song.url, applicationContext)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    // ── Android Auto browse tree ──────────────────────────────────────────

    private companion object {
        const val ROOT = "root"
        const val NODE_LIKED = "liked"
        const val NODE_DOWNLOADS = "downloads"
        const val NODE_PLAYLISTS = "playlists"
        const val NODE_ALBUMS = "albums"
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand("ACTION_CLOSE", Bundle.EMPTY))
                .add(SessionCommand("ACTION_TOGGLE_LIKE", Bundle.EMPTY))
                .add(SessionCommand("ACTION_NONE", Bundle.EMPTY))
                .build()

            val curId = currentSongState.songId.value
            val isLiked = io.github.sekademi.spotufi.data.preferences.isSongLiked(this@PlaybackService, curId.toString())
            val likeButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName(if (isLiked) "Unlike" else "Like")
                .setSessionCommand(SessionCommand("ACTION_TOGGLE_LIKE", Bundle.EMPTY))
                .setCustomIconResId(if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
                .build()

            val closeButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName("Close")
                .setSessionCommand(SessionCommand("ACTION_CLOSE", Bundle.EMPTY))
                .setCustomIconResId(R.drawable.ic_close)
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setCustomLayout(ImmutableList.of(likeButton, closeButton))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == "ACTION_TOGGLE_LIKE") {
                val curId = currentSongState.songId.value
                val curSong = currentSongState.queue.value.find { it.id == curId }
                if (curSong != null) {
                    val wasLiked = io.github.sekademi.spotufi.data.preferences.isSongLiked(this@PlaybackService, curId.toString())
                    if (wasLiked) {
                        io.github.sekademi.spotufi.data.preferences.removeLikedSongId(this@PlaybackService, curId.toString())
                    } else {
                        io.github.sekademi.spotufi.data.preferences.addLikedSongId(this@PlaybackService, curId.toString())
                        val artistList = curSong.artistIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            .ifEmpty { curSong.singer.split(",", "&", "/").map { it.trim() }.filter { it.isNotBlank() } }
                        if (artistList.isNotEmpty()) {
                            io.github.sekademi.spotufi.data.recommendation.TasteProfileEngine.recordLike(this@PlaybackService, artistList)
                        }
                    }
                    if (curSong.spotifyTrackId.isNotBlank()) {
                        SpotifySync.setTrackSaved(this@PlaybackService, curSong.spotifyTrackId, !wasLiked)
                    }
                    updateSessionCustomLayout(session)
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == "ACTION_CLOSE") {
                // Graceful shutdown on notification close: pause playback, dismiss foreground notification, and stop service
                SongPlayer.pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val point = io.github.sekademi.spotufi.data.preferences.loadRestorePoint(this@PlaybackService) ?:
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(emptyList(), C.INDEX_UNSET, C.TIME_UNSET))
            val items = point.queue.map { playable(it) }
            val startIdx = point.queue.indexOfFirst { it.id == point.song.id }.coerceAtLeast(0)
            if (isForPlayback && items.isNotEmpty()) {
                currentSongState.updateQueue(point.queue)
                currentSongState.updateSongState(
                    point.song.coverUri, point.song.title, point.song.singer, true,
                    point.song.id, startIdx, point.song.album,
                )
                point.repeatMode?.let { currentSongState.updateRepeatState(it) }
            }
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(items, startIdx, point.positionMs))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "Atify", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            val all = childrenOf(parentId)
            val paged = paginate(all, page, pageSize)
            LibraryResult.ofItemList(ImmutableList.copyOf(paged), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
            trackById[mediaId]?.let { LibraryResult.ofItem(playable(it), null) }
                ?: LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE),
        )

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = future {
            val results = lastSuccess(repository.searchSongs(query)).orEmpty()
            registerTracks("search/$query", results)
            session.notifySearchResultChanged(browser, query, results.size, params)
            LibraryResult.ofVoid(params)
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            val results = lastSuccess(repository.searchSongs(query)).orEmpty()
            val items = registerTracks("search/$query", results)
            val paged = paginate(items, page, pageSize)
            LibraryResult.ofItemList(ImmutableList.copyOf(paged), params)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val requested = mediaItems.firstOrNull()
            if (requested != null) {
                val song = trackById[requested.mediaId]
                if (song != null) {
                    val queue = queueByTrackId[requested.mediaId] ?: listOf(song)
                    currentSongState.updateQueue(queue)
                    val idx = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                    currentSongState.updateSongState(
                        song.coverUri, song.title, song.singer, true,
                        song.id, idx, song.album,
                    )
                    SongPlayer.playSong(song.url, applicationContext)
                } else {
                    extractSearchQuery(requested)?.let { handleVoiceQuery(it) }
                }
            }
            return Futures.immediateFuture(mediaItems)
        }

        // A browsed track was tapped in the car: queue the list it came from and
        // play through our own engine (streams are resolved lazily per track, so
        // we never hand the session a playlist of URIs).
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val requested = mediaItems.getOrNull(startIndex) ?: mediaItems.firstOrNull()
            val song = requested?.let { trackById[it.mediaId] }
            if (song != null) {
                val queue = queueByTrackId[requested.mediaId] ?: listOf(song)
                currentSongState.updateQueue(queue)
                val idx = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                currentSongState.updateSongState(
                    song.coverUri, song.title, song.singer, true,
                    song.id, idx, song.album,
                )
                SongPlayer.playSong(song.url, applicationContext)
            } else if (requested != null) {
                extractSearchQuery(requested)?.let { handleVoiceQuery(it) }
            }
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(emptyList(), C.INDEX_UNSET, C.TIME_UNSET),
            )
        }
    }

    private fun extractSearchQuery(item: MediaItem): String? {
        val req = item.requestMetadata
        val query = req.searchQuery?.takeIf { it.isNotBlank() }
            ?: req.extras?.getString(android.app.SearchManager.QUERY)?.takeIf { it.isNotBlank() }
            ?: req.extras?.getString(android.provider.MediaStore.EXTRA_MEDIA_TITLE)?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(
                req.extras?.getString(android.provider.MediaStore.EXTRA_MEDIA_ARTIST),
                req.extras?.getString(android.provider.MediaStore.EXTRA_MEDIA_ALBUM),
            ).joinToString(" ").takeIf { it.isNotBlank() }
            ?: item.mediaId.takeIf {
                it.isNotBlank() && !it.startsWith("folder/") &&
                    it !in setOf(ROOT, NODE_LIKED, NODE_DOWNLOADS, NODE_PLAYLISTS, NODE_ALBUMS)
            }
        return query?.trim()
    }

    private fun handleVoiceQuery(query: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val cleanQuery = query.trim()
            if (cleanQuery.isBlank()) return@launch

            // 1. Unified catalog search (songs, episodes, shows)
            val searchResults = runCatching {
                lastSuccess(repository.searchEverything(cleanQuery))
            }.getOrNull()

            val isPodcastQuery = cleanQuery.contains("podcast", ignoreCase = true) ||
                cleanQuery.contains("episode", ignoreCase = true)

            if (isPodcastQuery && !searchResults?.episodes.isNullOrEmpty()) {
                val episode = searchResults.episodes.first()
                withContext(Dispatchers.Main) {
                    currentSongState.updateQueue(searchResults.episodes)
                    currentSongState.updateSongState(
                        episode.coverUri, episode.title, episode.singer, true,
                        episode.id, 0, episode.album,
                    )
                    SongPlayer.playSong(episode.url, applicationContext)
                }
                return@launch
            }

            if (!searchResults?.songs.isNullOrEmpty()) {
                val topSong = searchResults.songs.first()
                withContext(Dispatchers.Main) {
                    currentSongState.updateQueue(searchResults.songs)
                    currentSongState.updateSongState(
                        topSong.coverUri, topSong.title, topSong.singer, true,
                        topSong.id, 0, topSong.album,
                    )
                    SongPlayer.playSong(topSong.url, applicationContext)
                }
                return@launch
            }

            if (!searchResults?.episodes.isNullOrEmpty()) {
                val episode = searchResults.episodes.first()
                withContext(Dispatchers.Main) {
                    currentSongState.updateQueue(searchResults.episodes)
                    currentSongState.updateSongState(
                        episode.coverUri, episode.title, episode.singer, true,
                        episode.id, 0, episode.album,
                    )
                    SongPlayer.playSong(episode.url, applicationContext)
                }
                return@launch
            }

            // 2. Fallback: Search via YouTube directly
            val ytFilter = if (isPodcastQuery) com.metrolist.innertube.YouTube.SearchFilter.FILTER_VIDEO else com.metrolist.innertube.YouTube.SearchFilter.FILTER_SONG
            val ytHits = runCatching {
                com.metrolist.innertube.YouTube.search(cleanQuery, ytFilter).getOrNull()?.items
                    ?.mapNotNull { item ->
                        when (item) {
                            is com.metrolist.innertube.models.SongItem -> item
                            is com.metrolist.innertube.models.EpisodeItem -> item.asSongItem()
                            else -> null
                        }
                    }
            }.getOrNull()
            val topYt = ytHits?.firstOrNull()
            if (topYt != null) {
                val model = SongsModel(
                    id = topYt.id.hashCode(),
                    title = topYt.title,
                    singer = topYt.artists.joinToString(", ") { it.name }.ifBlank { "Unknown" },
                    album = topYt.album?.name ?: "Voice Search",
                    coverUri = topYt.thumbnail,
                    url = topYt.id,
                    durationMs = (topYt.duration ?: 0) * 1000
                )
                withContext(Dispatchers.Main) {
                    currentSongState.updateQueue(listOf(model))
                    currentSongState.updateSongState(
                        model.coverUri, model.title, model.singer, true,
                        model.id, 0, model.album,
                    )
                    SongPlayer.playSong(model.url, applicationContext)
                }
            }
        }
    }

    private suspend fun childrenOf(parentId: String): List<MediaItem> = when {
        parentId == ROOT -> listOf(
            folder(NODE_LIKED, "Liked Songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            folder(NODE_DOWNLOADS, "Downloads", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            folder(NODE_PLAYLISTS, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
            folder(NODE_ALBUMS, "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
        )
        parentId == NODE_LIKED ->
            registerTracks(NODE_LIKED, lastSuccess(repository.provideLikedSongs()).orEmpty())
        parentId == NODE_DOWNLOADS ->
            registerTracks(
                NODE_DOWNLOADS,
                io.github.sekademi.spotufi.data.preferences.getDownloadedSongs(applicationContext),
            )
        parentId == NODE_PLAYLISTS ->
            libraryEntries().filter {
                it.isPlaylist && it.spotifyId != Api.LIKED_SONGS_ID && it.spotifyId != Api.DOWNLOADS_ID
            }.map { folder("playlist/${it.spotifyId}", it.name, MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS, it.coverUri) }
        parentId == NODE_ALBUMS ->
            libraryEntries().filter { !it.isPlaylist }.map {
                folder(
                    "album/${android.net.Uri.encode(it.name)}/${android.net.Uri.encode(it.artists)}",
                    it.name,
                    MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
                    it.coverUri,
                )
            }
        parentId.startsWith("playlist/") -> {
            val songs = lastSuccess(repository.providePlaylistSongs(parentId.removePrefix("playlist/")))
            registerTracks(parentId, songs.orEmpty())
        }
        parentId.startsWith("album/") -> {
            val parts = parentId.removePrefix("album/").split('/')
            val name = android.net.Uri.decode(parts.getOrElse(0) { "" })
            val artist = android.net.Uri.decode(parts.getOrElse(1) { "" })
            registerTracks(parentId, lastSuccess(repository.provideAlbumSongs(name, artist)).orEmpty())
        }
        else -> emptyList()
    }

    private suspend fun libraryEntries() =
        lastSuccess(repository.provideLibrary()).orEmpty()

    /** Runs a paged/cached response flow to completion and keeps the final data. */
    private suspend fun <T> lastSuccess(flow: Flow<Response<T>>): T? =
        runCatching { flow.toList() }.getOrNull()
            ?.filterIsInstance<Response.Success<T>>()
            ?.lastOrNull()?.data

    private fun registerTracks(parentId: String, songs: List<SongsModel>): List<MediaItem> {
        songs.forEach { song ->
            trackById["song/${song.id}"] = song
            queueByTrackId["song/${song.id}"] = songs
        }
        return songs.map { playable(it) }
    }

    private fun folder(id: String, title: String, mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, coverUri: String = ""): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(mediaType)
                    .apply { if (coverUri.isNotBlank()) setArtworkUri(android.net.Uri.parse(coverUri)) }
                    .build(),
            )
            .build()

    private fun playable(song: SongsModel): MediaItem =
        MediaItem.Builder()
            .setMediaId("song/${song.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.singer)
                    .setAlbumTitle(song.album)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .apply { if (song.coverUri.isNotBlank()) setArtworkUri(android.net.Uri.parse(song.coverUri)) }
                    .build(),
            )
            .build()

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val f = SettableFuture.create<T>()
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                f.set(block())
            } catch (e: Exception) {
                f.setException(e)
            }
        }
        return f
    }

    private fun <T> paginate(list: List<T>, page: Int, pageSize: Int): List<T> {
        if (pageSize <= 0 || page < 0) return list
        val fromIndex = page * pageSize
        if (fromIndex >= list.size) return emptyList()
        val toIndex = minOf(fromIndex + pageSize, list.size)
        return list.subList(fromIndex, toIndex)
    }

    private fun updateSessionCustomLayout(session: MediaSession) {
        val curId = currentSongState.songId.value
        val isLiked = io.github.sekademi.spotufi.data.preferences.isSongLiked(this, curId.toString())
        val likeButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(if (isLiked) "Unlike" else "Like")
            .setSessionCommand(SessionCommand("ACTION_TOGGLE_LIKE", Bundle.EMPTY))
            .setCustomIconResId(if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
            .build()

        val closeButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("Close")
            .setSessionCommand(SessionCommand("ACTION_CLOSE", Bundle.EMPTY))
            .setCustomIconResId(R.drawable.ic_close)
            .build()

        session.setCustomLayout(ImmutableList.of(likeButton, closeButton))
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // If playback is paused or inactive, tear the service down when swiped away from Recents.
        // If playing, keep playing in the background (standard Android music player behavior).
        val isPlaying = SongPlayer.exoPlayer?.isPlaying == true || SongPlayer.webPlaybackActive()
        if (!isPlaying) {
            SongPlayer.pause()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        io.github.sekademi.spotufi.connect.AtifyConnectServer.onActionNext = null
        io.github.sekademi.spotufi.connect.AtifyConnectServer.onActionPrev = null
        io.github.sekademi.spotufi.connect.AtifyConnectServer.stop()
        SongPlayer.exoPlayer?.removeListener(playerListener)
        SongPlayer.onPlayerSwapped = null
        SpotifyWebPlayer.onStateChanged = null
        webPlayer?.release()
        webPlayer = null
        mediaSession?.release()
        mediaSession = null
        SongPlayer.release()
        super.onDestroy()
    }
}

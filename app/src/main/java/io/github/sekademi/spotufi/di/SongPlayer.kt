package io.github.sekademi.spotufi.di

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.metrolist.innertube.utils.YouTubeUrlParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import android.graphics.Bitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * Core audio playback orchestrator. Delegates stream resolution to [StreamResolver],
 * candidate scoring to [CandidateScorer], downloads to [DownloadManager], and
 * crossfade/DJ mixing to [CrossfadeEngine].
 */
object SongPlayer {
    private const val TAG = "SongPlayer"
    private var player: ExoPlayer? = null
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var appCtx: Context? = null
    @Volatile private var currentPlayerFilter: io.github.sekademi.spotufi.audio.CrossfadeFilterAudioProcessor? = null
    private val streamCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val sourceCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val videoCandidatesCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    // ── Stream resolution delegation ──
    var currentSource: String
        get() = StreamResolver.currentSource
        set(value) { StreamResolver.currentSource = value }
    var currentQuality: String
        get() = StreamResolver.currentQuality
        set(value) { StreamResolver.currentQuality = value }
    var losslessStreaming: Boolean
        get() = StreamResolver.losslessStreaming
        set(value) { StreamResolver.losslessStreaming = value }
    var losslessHiRes: Boolean
        get() = StreamResolver.losslessHiRes
        set(value) { StreamResolver.losslessHiRes = value }
    @Volatile var webPlayerEnabled = false
    var youtubeEnabled: Boolean
        get() = StreamResolver.youtubeEnabled
        set(value) { StreamResolver.youtubeEnabled = value }

    fun registerLossless(pairs: List<Pair<String, String>>) = StreamResolver.registerLossless(pairs)
    fun registerAlternativeKeys(pairs: List<Pair<String, String>>) = StreamResolver.registerAlternativeKeys(pairs)
    fun registerExplicit(pairs: List<Pair<String, Boolean>>) = StreamResolver.registerExplicit(pairs)
    fun registerDuration(pairs: List<Pair<String, Int>>) = StreamResolver.registerDuration(pairs)
    fun registerMetadata(pairs: List<Pair<String, CandidateScorer.TrackMatchMetadata>>) = StreamResolver.registerMetadata(pairs)
    fun invalidateResolvedStream(song: String) {
        streamCache.remove(song)
        sourceCache.remove(song)
        videoCandidatesCache.remove("$song|FILTER_SONG")
        videoCandidatesCache.remove("$song|FILTER_VIDEO")
        appCtx?.let { ctx ->
            io.github.sekademi.spotufi.data.preferences.clearCachedVideoId(ctx, song)
            io.github.sekademi.spotufi.data.preferences.clearCachedStream(ctx, song)
        }
        StreamResolver.invalidateResolvedStream(song, appCtx)
    }
    fun buildSpotifyPlayQuery(spotifyTrackId: String, title: String, artist: String) = StreamResolver.buildSpotifyPlayQuery(spotifyTrackId, title, artist)
    fun searchTextForPlayback(song: String) = StreamResolver.searchTextForPlayback(song)
    fun videoIdFromYouTubeLink(text: String): String? =
        YouTubeUrlParser.extractVideoId(text)
            ?: text.trim().takeIf { it.matches(Regex("""[A-Za-z0-9_-]{11}""")) }

    @Volatile private var boundState: CurrentSongState? = null
    @Volatile private var lastYtFailureReason: String? = null

    private val silenceProcessors = java.util.Collections.newSetFromMap(java.util.WeakHashMap<androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor, Boolean>())

    fun setSkipSilence(enabled: Boolean) {
        synchronized(silenceProcessors) {
            silenceProcessors.forEach { it.setEnabled(enabled) }
        }
    }

    private val vocalRemovers = java.util.Collections.newSetFromMap(java.util.WeakHashMap<io.github.sekademi.spotufi.audio.VocalRemoverAudioProcessor, Boolean>())
    private val _vocalAttenuation = MutableStateFlow(0f)
    val vocalAttenuation: StateFlow<Float> = _vocalAttenuation.asStateFlow()

    fun setVocalAttenuation(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _vocalAttenuation.value = clamped
        synchronized(vocalRemovers) {
            vocalRemovers.forEach { it.attenuation = clamped }
        }
    }

    fun getVocalAttenuation(): Float = _vocalAttenuation.value

    private fun updateResolveStatus(isResolving: Boolean, status: String = "") {
        boundState?.updateResolveState(isResolving, status)
    }

    // ── Download delegation ──
    @Volatile var onDownloadsChanged: (() -> Unit)? = null
        set(value) { field = value; DownloadManager.onDownloadsChanged = value }
    @Volatile var lastDownloadError: String? = null
        get() = DownloadManager.lastDownloadError

    fun isDownloading(query: String): Boolean = DownloadManager.isDownloading(query)
    fun downloadProgress(query: String): Int = DownloadManager.downloadProgress(query)
    fun downloadingSnapshot(): List<Pair<io.github.sekademi.spotufi.data.entity.SongsModel, Int>> = DownloadManager.downloadingSnapshot()
    fun downloadAll(songs: List<io.github.sekademi.spotufi.data.entity.SongsModel>, context: Context) = DownloadManager.downloadAll(songs, context, scope)
    fun allDownloaded(songs: List<io.github.sekademi.spotufi.data.entity.SongsModel>, context: Context): Boolean = DownloadManager.allDownloaded(songs, context)
    fun downloadSong(song: io.github.sekademi.spotufi.data.entity.SongsModel, context: Context, onComplete: (Boolean) -> Unit = {}) = DownloadManager.downloadSong(song, context, scope, onComplete)

    // ── Crossfade delegation ──
    @Volatile var onPlayerSwapped: ((ExoPlayer) -> Unit)? = null
    fun isCrossfadeActive(): Boolean = CrossfadeEngine.isCrossfadeActive()
    private fun cancelCrossfade() = CrossfadeEngine.cancelCrossfade()
    private fun startPositionWatch() = CrossfadeEngine.startPositionWatch(scope)
    private fun triggerCrossfade(ctx: Context, configuredMs: Int) = CrossfadeEngine.triggerCrossfade(ctx, configuredMs, scope)

    fun initCrossfade(appContext: Context, state: CurrentSongState) {
        CrossfadeEngine.init(appContext, state)
        CrossfadeEngine.onPlayerSwapped = { onPlayerSwapped?.invoke(it) }
        boundState = state
    }

    // ── Media notification metadata ──
    @Volatile private var metaTitle: String = ""
    @Volatile private var metaArtist: String = ""
    @Volatile private var metaCover: String = ""
    @Volatile private var metaArtworkData: ByteArray? = null
    @Volatile private var currentRequest: String = ""
    @Volatile private var loadedQuery: String? = null
    @Volatile private var playWhenResolved = true

    fun setNowPlayingMeta(title: String, artist: String, coverUri: String) {
        metaTitle = title
        metaArtist = artist
        if (metaCover != coverUri) {
            metaCover = coverUri
            metaArtworkData = null
            val ctx = appCtx
            if (ctx != null && coverUri.isNotBlank()) {
                val request = ImageRequest.Builder(ctx)
                    .data(coverUri)
                    // 384×384 is more than sufficient for system media notifications
                    // and reduces the bitmap from ~4 MB to ~450 KB before JPEG compression.
                    .size(coil3.size.Size(384, 384))
                    .allowHardware(false)
                    .target(
                        onSuccess = { result ->
                            runCatching {
                                val bitmap = result.toBitmap()
                                val stream = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                                // Release the decoded bitmap immediately — the JPEG bytes
                                // are the only thing we need to keep.
                                bitmap.recycle()
                                val bytes = stream.toByteArray()
                                metaArtworkData = bytes
                                updatePlayerArtwork(bytes, coverUri)
                            }
                        }
                    )
                    .build()
                ctx.imageLoader.enqueue(request)
            }
        }
    }

    private fun updatePlayerArtwork(bytes: ByteArray, coverUri: String) {
        scope.launch(Dispatchers.Main) {
            val p = player ?: return@launch
            val currentItem = p.currentMediaItem ?: return@launch
            val newMetadata = currentItem.mediaMetadata.buildUpon()
                .setArtworkData(bytes, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                .apply { if (coverUri.isNotBlank()) setArtworkUri(android.net.Uri.parse(coverUri)) }
                .build()
            val newItem = currentItem.buildUpon()
                .setMediaMetadata(newMetadata)
                .build()
            p.replaceMediaItem(p.currentMediaItemIndex, newItem)
        }
    }

    // ── Stream resolution (delegates to StreamResolver with caching) ──
    private suspend fun resolveStreamUrl(song: String, appContext: Context, forPlayback: Boolean = false): String? {
        streamCache[song]?.let {
            if (forPlayback) {
                currentSource = sourceCache[song] ?: "YouTube"
                updateResolveStatus(false)
            }
            return it
        }
        return StreamResolver.resolveStreamUrl(song, appContext, forPlayback)
    }

    suspend fun resolveYtPlaybackPublic(
        query: String,
        audioQuality: com.metrolist.music.constants.AudioQuality,
        appContext: Context,
    ): com.metrolist.music.utils.YTPlayerUtils.PlaybackData? = StreamResolver.resolveYtPlayback(query, audioQuality, appContext)

    fun resolveStreamUrlPublic(song: String, appContext: Context, forPlayback: Boolean = false): String? =
        kotlinx.coroutines.runBlocking { StreamResolver.resolveStreamUrl(song, appContext, forPlayback) }

    // ── MediaItem + ExoPlayer ──
    private fun buildMediaItem(streamUrl: String, mimeType: String? = null, mediaId: String? = null): MediaItem {
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(metaTitle)
            .setArtist(metaArtist)
            .apply {
                if (metaCover.isNotBlank()) setArtworkUri(android.net.Uri.parse(metaCover))
                metaArtworkData?.let { setArtworkData(it, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
            }
            .build()
        return MediaItem.Builder()
            .apply { if (!mediaId.isNullOrBlank()) setMediaId(mediaId) }
            .setUri(streamUrl)
            .apply { if (mimeType != null) setMimeType(mimeType) }
            .setMediaMetadata(metadata)
            .build()
    }

    fun buildMediaItemWithSong(streamUrl: String, song: io.github.sekademi.spotufi.data.entity.SongsModel): MediaItem {
        val mimeType = when {
            streamUrl.contains(".flac", ignoreCase = true) -> androidx.media3.common.MimeTypes.AUDIO_FLAC
            streamUrl.contains(".mp4", ignoreCase = true) || streamUrl.contains(".m4a", ignoreCase = true) -> androidx.media3.common.MimeTypes.AUDIO_MP4
            streamUrl.contains(".webm", ignoreCase = true) || streamUrl.contains(".opus", ignoreCase = true) -> androidx.media3.common.MimeTypes.AUDIO_OPUS
            else -> null
        }
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.singer)
            .setAlbumTitle(song.album)
            .apply {
                if (song.coverUri.isNotBlank()) setArtworkUri(android.net.Uri.parse(song.coverUri))
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(streamUrl)
            .apply { if (mimeType != null) setMimeType(mimeType) }
            .setMediaMetadata(metadata)
            .build()
    }

    fun buildMediaItemPublic(streamUrl: String): MediaItem = buildMediaItem(streamUrl)
    fun buildAudioAttributesPublic() = buildAudioAttributes()

    private fun buildAudioAttributes() =
        androidx.media3.common.AudioAttributes.Builder()
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .build()

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun createPlayerWithFilterPublic(
        context: Context,
        handleAudioFocus: Boolean,
    ): Pair<ExoPlayer, io.github.sekademi.spotufi.audio.CrossfadeFilterAudioProcessor> =
        createPlayerWithFilter(context, handleAudioFocus)

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun createPlayerWithFilter(
        context: Context,
        handleAudioFocus: Boolean,
    ): Pair<ExoPlayer, io.github.sekademi.spotufi.audio.CrossfadeFilterAudioProcessor> {
        val filter = io.github.sekademi.spotufi.audio.CrossfadeFilterAudioProcessor()
        val renderers = object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): androidx.media3.exoplayer.audio.AudioSink {
                val silenceProcessor = androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor()
                silenceProcessor.setEnabled(io.github.sekademi.spotufi.data.preferences.isSkipSilenceEnabled(context))
                synchronized(silenceProcessors) {
                    silenceProcessors.add(silenceProcessor)
                }

                val vocalRemover = io.github.sekademi.spotufi.audio.VocalRemoverAudioProcessor()
                vocalRemover.attenuation = _vocalAttenuation.value
                synchronized(vocalRemovers) {
                    vocalRemovers.add(vocalRemover)
                }

                val sink = androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(true)
                    .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
                    .setAudioProcessorChain(
                        androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain(filter, silenceProcessor, vocalRemover),
                    )
                    .build()
                if (io.github.sekademi.spotufi.data.preferences.isAudioOffloadEnabled(context)) {
                    sink.setOffloadMode(androidx.media3.exoplayer.audio.AudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_NOT_REQUIRED)
                }
                return sink
            }
        }
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 2_500,
                /* maxBufferMs = */ 30_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val p = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(StreamResolver.cacheDataSourceFactory(context)),
            )
            .setLoadControl(loadControl)
            .setRenderersFactory(renderers)
            .setAudioAttributes(buildAudioAttributes(), handleAudioFocus)
            .setHandleAudioBecomingNoisy(handleAudioFocus)
            .build()
        return p to filter
    }

    @Volatile private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null

    fun updateVolumeNormalization(context: Context, audioSessionId: Int) {
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = null
            if (io.github.sekademi.spotufi.data.preferences.isVolumeNormalizationEnabled(context) &&
                audioSessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET
            ) {
                loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(audioSessionId).apply {
                    setTargetGain(150)
                    enabled = true
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("SongPlayer", "Failed to configure LoudnessEnhancer: ${e.message}")
        }
        io.github.sekademi.spotufi.audio.EqualizerEngine.bindAudioSession(context, audioSessionId)
    }

    private fun ensurePlayer(context: Context) {
        appCtx = context.applicationContext
        if (player == null) {
            val (p, filter) = createPlayerWithFilter(context, handleAudioFocus = true)
            player = p
            currentPlayerFilter = filter
            CrossfadeEngine.bindPrimaryFilter(filter)
            updateVolumeNormalization(context, p.audioSessionId)
            onPlayerCreated?.invoke(p)
        }
    }

    fun promotePlayer(incoming: ExoPlayer, filter: io.github.sekademi.spotufi.audio.CrossfadeFilterAudioProcessor?) {
        val oldPlayer = player
        player = incoming
        currentPlayerFilter = filter
        CrossfadeEngine.bindPrimaryFilter(filter)
        appCtx?.let { updateVolumeNormalization(it, incoming.audioSessionId) }
        // Release the outgoing player so it doesn't leak its AudioTrack, audio-session
        // handle, and decoder threads. Must run on the main thread per ExoPlayer contract.
        if (oldPlayer != null && oldPlayer !== incoming) {
            scope.launch(Dispatchers.Main) {
                runCatching {
                    oldPlayer.stop()
                    oldPlayer.clearMediaItems()
                    oldPlayer.release()
                }
            }
        }
    }

    val exoPlayer: ExoPlayer? get() = player
    fun ensureCreated(context: Context) = ensurePlayer(context.applicationContext)
    @Volatile var onPlayerCreated: ((ExoPlayer) -> Unit)? = null

    // ── Playback controls ──
    fun isPlaying(): Boolean {
        if (webPlaybackActive()) return SpotifyWebPlayer.isPlaying
        return player?.isPlaying ?: false
    }

    fun webPlaybackActive(): Boolean {
        if (!webPlayerEnabled) return false
        val ctx = appCtx ?: return false
        return io.github.sekademi.spotufi.data.preferences.isWebPlaybackEnabled(ctx) &&
            SpotifyWebPlayer.canPlay &&
            io.github.sekademi.spotufi.data.api.SpotifySession.spDc(ctx).isNotBlank()
    }

    // ── Session restore ──
    @Volatile private var restoreQuery: String? = null
    @Volatile private var restorePositionMs: Long = 0L

    fun setRestorePoint(query: String, positionMs: Long) {
        if (query.isBlank()) return
        restoreQuery = query
        restorePositionMs = positionMs.coerceAtLeast(0L)
        loadedQuery = query
    }

    fun playSong(song: String, context: Context) {
        val appContext = context.applicationContext
        appCtx = appContext
        currentRequest = song
        playWhenResolved = true
        boundState?.setSongUrl(song)
        cancelCrossfade()
        runCatching {
            player?.pause()
            player?.clearMediaItems()
        }

        if (song.startsWith("episode:") && webPlayerEnabled && SpotifyWebPlayer.canPlay &&
            io.github.sekademi.spotufi.data.preferences.isWebPlaybackEnabled(appContext)
        ) {
            runCatching { player?.pause() }
            SpotifyWebPlayer.playEpisode(song.removePrefix("episode:"))
            return
        }

        val downloadedPath = io.github.sekademi.spotufi.data.preferences.downloadedPathForQuery(appContext, song)
        if (downloadedPath == null && webPlayerEnabled &&
            io.github.sekademi.spotufi.data.preferences.isWebPlaybackEnabled(appContext) &&
            SpotifyWebPlayer.canPlay
        ) {
            val spotifyId = StreamResolver.spotifyTrackIdForPlayback(song)
            if (spotifyId != null) {
                runCatching { player?.pause() }
                currentSource = "Spotify"
                currentQuality = ""
                updateResolveStatus(true, "Loading Spotify track...")
                SpotifyWebPlayer.play(spotifyId)
                scope.launch {
                    delay(1500)
                    updateResolveStatus(false)
                }
                return
            }
            Log.w(TAG, "web playback on but no Spotify id for query: $song — using fallback engine")
        }

        scope.launch {
            try {
                val streamUrl = resolveStreamUrl(song, appContext, forPlayback = true) ?: run {
                    if (currentRequest == song) withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            appContext, "Couldn't find a playable stream for this track",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                    if (boundState?.resolveError?.value == null) {
                        boundState?.updateResolveError("No stream found")
                    }
                    updateResolveStatus(false)
                    return@launch
                }
                if (currentRequest != song) {
                    updateResolveStatus(false)
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    if (currentRequest != song) {
                        updateResolveStatus(false)
                        return@withContext
                    }
                    ensurePlayer(appContext)
                    val currentSongModel = boundState?.queue?.value?.firstOrNull { it.url == song }
                    val initialItem = if (currentSongModel != null) {
                        buildMediaItemWithSong(streamUrl, currentSongModel)
                    } else {
                        buildMediaItem(streamUrl, mediaId = song)
                    }
                    player!!.setMediaItem(initialItem)
                    player!!.prepare()
                    if (song == restoreQuery && restorePositionMs > 0) {
                        player!!.seekTo(restorePositionMs)
                    }
                    restoreQuery = null
                    player!!.playWhenReady = playWhenResolved
                    loadedQuery = song
                    updateResolveStatus(false)
                }
                startPositionWatch()

                val currentQueue = boundState?.queue?.value.orEmpty()
                val currentIndex = currentQueue.indexOfFirst { it.url == song }
                if (currentIndex in 0 until currentQueue.lastIndex) {
                    val nextSong = currentQueue[currentIndex + 1]
                    prefetch(nextSong.url, appContext)
                    queueNextMediaItem(nextSong, appContext)
                }
            } catch (e: Exception) {
                Log.e(TAG, "playSong failed for query: $song", e)
                boundState?.updateResolveError(e.message ?: "Playback failed")
                updateResolveStatus(false)
            }
        }
    }

    /**
     * Proactively resolves and appends the next MediaItem to ExoPlayer for sample-accurate gapless playback.
     */
    fun queueNextMediaItem(nextSong: io.github.sekademi.spotufi.data.entity.SongsModel, context: Context) {
        val crossfadeMs = io.github.sekademi.spotufi.data.preferences.getCrossfadeMs(context)
        if (crossfadeMs > 0) return
        val p = player ?: return
        scope.launch {
            val url = runCatching { resolveStreamUrl(nextSong.url, context, forPlayback = false) }.getOrNull()
            if (url.isNullOrBlank()) return@launch
            withContext(Dispatchers.Main) {
                if (player === p && p.mediaItemCount == 1) {
                    val nextItem = buildMediaItemWithSong(url, nextSong)
                    p.addMediaItem(nextItem)
                    Log.d(TAG, "Gapless: Pre-queued next MediaItem: ${nextSong.title}")
                }
            }
        }
    }

    /**
     * Returns live technical metadata regarding the active audio stream.
     */
    fun getAudioStreamDetails(): io.github.sekademi.spotufi.data.entity.AudioStreamDetails {
        val format = player?.audioFormat
        val mime = format?.sampleMimeType.orEmpty()
        val cleanMime = when {
            mime.contains("opus", ignoreCase = true) -> "Opus"
            mime.contains("mp4a", ignoreCase = true) || mime.contains("aac", ignoreCase = true) -> "AAC"
            mime.contains("flac", ignoreCase = true) -> "FLAC"
            mime.contains("mpeg", ignoreCase = true) || mime.contains("mp3", ignoreCase = true) -> "MP3"
            mime.contains("vorbis", ignoreCase = true) -> "Vorbis"
            mime.isNotBlank() -> mime.substringAfterLast('/')
            else -> ""
        }
        val ctx = appCtx
        val isOffload = ctx != null && io.github.sekademi.spotufi.data.preferences.isAudioOffloadEnabled(ctx)
        return io.github.sekademi.spotufi.data.entity.AudioStreamDetails(
            source = currentSource,
            quality = currentQuality,
            format = cleanMime,
            sampleRateHz = format?.sampleRate ?: 0,
            channelCount = format?.channelCount ?: 0,
            bitrateBps = format?.bitrate ?: 0,
            isOffloadActive = isOffload,
        )
    }

    fun prefetch(song: String, context: Context) {
        if (song.isBlank()) return
        val appContext = context.applicationContext
        if (webPlaybackActive()) return
        scope.launch(Dispatchers.IO) {
            val url = runCatching { resolveStreamUrl(song, appContext) }.getOrNull()
            if (url != null) StreamResolver.cacheIntro(url, appContext)
        }
    }

    fun prefetchList(songs: List<String>, context: Context, count: Int = 2) {
        if (songs.isEmpty() || webPlaybackActive()) return
        val appContext = context.applicationContext
        val targets = songs.filter { it.isNotBlank() }.take(count)
        scope.launch(Dispatchers.IO) {
            targets.forEach { song ->
                if (streamCache[song] == null && io.github.sekademi.spotufi.data.preferences.getCachedStream(appContext, song) == null) {
                    val url = runCatching { StreamResolver.resolveStreamUrl(song, appContext, forPlayback = false) }.getOrNull()
                    if (url != null) {
                        StreamResolver.cacheIntro(url, appContext)
                    }
                }
            }
        }
    }

    fun play() {
        if (webPlaybackActive()) { SpotifyWebPlayer.resume(); return }
        playWhenResolved = true
        if (currentRequest.isNotBlank() && currentRequest != loadedQuery) {
            return
        }
        if ((player?.mediaItemCount ?: 0) == 0) {
            val q = restoreQuery
            val ctx = appCtx
            if (q != null && ctx != null) { playSong(q, ctx); return }
        }
        player?.play()
    }

    fun pause() {
        cancelCrossfade()
        playWhenResolved = false
        if (webPlaybackActive()) { SpotifyWebPlayer.pause(); return }
        player?.let {
            it.playWhenReady = false
            appCtx?.let { ctx ->
                val pos = it.currentPosition
                if (pos > 0) io.github.sekademi.spotufi.data.preferences.saveLastPosition(ctx, pos)
            }
        }
    }

    fun stop() {
        cancelCrossfade()
        player?.stop()
        loadedQuery = null
        currentRequest = ""
    }

    fun seekTo(position: Long) {
        cancelCrossfade()
        if (webPlaybackActive()) { SpotifyWebPlayer.seekTo(position); return }
        player?.seekTo(position)
    }

    fun release() {
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        CrossfadeEngine.release()
        player?.release()
        player = null
        loadedQuery = null
        currentRequest = ""
    }

    fun getDuration(): Long {
        if (webPlaybackActive()) return SpotifyWebPlayer.durationMs
        return player?.duration ?: 0L
    }

    fun getCurrentPosition(): Long {
        if (webPlaybackActive()) return SpotifyWebPlayer.positionMs
        return player?.currentPosition ?: 0L
    }

    fun isPrepared(): Boolean {
        val playerState = player?.playbackState
        return playerState != null && playerState != ExoPlayer.STATE_IDLE && playerState != ExoPlayer.STATE_ENDED
    }

    // ── Sleep timer ──
    @Volatile private var sleepJob: kotlinx.coroutines.Job? = null
    @Volatile var sleepTimerEndAt: Long = 0L
        private set

    private val _sleepTimerRemainingSec = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingSec: StateFlow<Long?> = _sleepTimerRemainingSec.asStateFlow()

    private val _sleepTimerStopAtTrackEnd = MutableStateFlow(false)
    val sleepTimerStopAtTrackEnd: StateFlow<Boolean> = _sleepTimerStopAtTrackEnd.asStateFlow()

    fun setSleepTimer(durationMillis: Long) {
        cancelSleepTimer()
        if (durationMillis <= 0L) return

        val endTime = System.currentTimeMillis() + durationMillis
        sleepTimerEndAt = endTime
        _sleepTimerRemainingSec.value = durationMillis / 1000L

        sleepJob = scope.launch {
            val fadeDurationMs = 15_000L.coerceAtMost(durationMillis)

            // Countdown loop until fade-out threshold
            while (true) {
                val now = System.currentTimeMillis()
                val remainingMs = sleepTimerEndAt - now
                if (remainingMs <= fadeDurationMs) break
                _sleepTimerRemainingSec.value = (remainingMs / 1000L).coerceAtLeast(1L)
                delay(1000L.coerceAtMost(remainingMs - fadeDurationMs))
            }

            // Smooth volume fade-out over the final seconds
            val fadeStart = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - fadeStart
                val remainingFade = (fadeDurationMs - elapsed).coerceAtLeast(0L)
                _sleepTimerRemainingSec.value = remainingFade / 1000L
                val factor = (remainingFade.toFloat() / fadeDurationMs.toFloat()).coerceIn(0f, 1f)
                withContext(Dispatchers.Main) {
                    player?.volume = factor
                }
                if (elapsed >= fadeDurationMs) break
                delay(300L)
            }

            withContext(Dispatchers.Main) {
                pause()
                player?.volume = 1f
            }
            _sleepTimerRemainingSec.value = null
            sleepTimerEndAt = 0L
        }
    }

    fun setSleepTimerAtTrackEnd() {
        cancelSleepTimer()
        _sleepTimerStopAtTrackEnd.value = true

        sleepJob = scope.launch {
            val initialTrack = loadedQuery
            val fadeDurationMs = 15_000L

            while (_sleepTimerStopAtTrackEnd.value) {
                // If song changed or stopped, finish timer
                if (loadedQuery != initialTrack) {
                    withContext(Dispatchers.Main) {
                        pause()
                        player?.volume = 1f
                    }
                    break
                }

                val dur = getDuration()
                val pos = getCurrentPosition()

                if (dur > 0L && pos > 0L) {
                    val remainingMs = dur - pos
                    if (remainingMs <= fadeDurationMs) {
                        val factor = (remainingMs.toFloat() / fadeDurationMs.toFloat()).coerceIn(0f, 1f)
                        withContext(Dispatchers.Main) {
                            player?.volume = factor
                        }
                    }
                    if (remainingMs <= 400L || !isPrepared()) {
                        withContext(Dispatchers.Main) {
                            pause()
                            player?.volume = 1f
                        }
                        break
                    }
                }
                delay(400L)
            }

            withContext(Dispatchers.Main) {
                player?.volume = 1f
            }
            _sleepTimerStopAtTrackEnd.value = false
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        sleepTimerEndAt = 0L
        _sleepTimerRemainingSec.value = null
        _sleepTimerStopAtTrackEnd.value = false
        scope.launch(Dispatchers.Main) {
            player?.volume = 1f
        }
    }
}

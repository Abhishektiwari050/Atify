package io.github.sekademi.spotufi.ui.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.session.MediaButtonReceiver
import io.github.sekademi.spotufi.MainActivity
import io.github.sekademi.spotufi.di.RepeatMode
import io.github.sekademi.spotufi.di.SongPlayer

/**
 * Shared singleton holder for [MediaSessionCompat] across [PlaybackService]
 * and [MusicBrowserService] to ensure both services share the exact same active
 * session token, transport controls, and playback state for Google Assistant and Android.
 */
object MediaSessionHolder {

    @Volatile
    var mediaSessionCompat: MediaSessionCompat? = null
        private set

    fun getOrCreateSession(
        context: Context,
        callback: MediaSessionCompat.Callback? = null
    ): MediaSessionCompat {
        val existing = mediaSessionCompat
        if (existing != null) {
            existing.isActive = true
            callback?.let { existing.setCallback(it) }
            return existing
        }

        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            context, 0, activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val mediaButtonReceiverIntent = Intent(Intent.ACTION_MEDIA_BUTTON, null, context, MediaButtonReceiver::class.java)
        val mediaButtonPendingIntent = PendingIntent.getBroadcast(
            context, 0, mediaButtonReceiverIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val session = MediaSessionCompat(context.applicationContext, "AtifyMediaSession").apply {
            setMediaButtonReceiver(mediaButtonPendingIntent)
            setSessionActivity(sessionActivity)
            callback?.let { setCallback(it) }
            isActive = true
        }
        mediaSessionCompat = session

        // Immediately publish an active initial PlaybackStateCompat so the session is
        // ready and discoverable by assistants from the moment of initialization.
        updatePlaybackState(
            state = PlaybackStateCompat.STATE_NONE,
            position = 0L
        )
        return session
    }

    fun updatePlaybackState(
        state: Int,
        position: Long = SongPlayer.getCurrentPosition(),
        repeatMode: RepeatMode = RepeatMode.OFF,
        isShuffle: Boolean = false
    ) {
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_FAST_FORWARD or
                PlaybackStateCompat.ACTION_REWIND or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_PREPARE or
                PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH or
                PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_PREPARE_FROM_URI or
                PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_PLAY_FROM_URI or
                PlaybackStateCompat.ACTION_SET_REPEAT_MODE or
                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE

        val playbackSpeed = if (state == PlaybackStateCompat.STATE_PLAYING) 1.0f else 0.0f
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, position.coerceAtLeast(0L), playbackSpeed, android.os.SystemClock.elapsedRealtime())

        mediaSessionCompat?.let { session ->
            session.isActive = true
            session.setPlaybackState(stateBuilder.build())
            session.setRepeatMode(
                when (repeatMode) {
                    RepeatMode.OFF -> PlaybackStateCompat.REPEAT_MODE_NONE
                    RepeatMode.ONE -> PlaybackStateCompat.REPEAT_MODE_ONE
                    RepeatMode.ALL -> PlaybackStateCompat.REPEAT_MODE_ALL
                }
            )
            session.setShuffleMode(
                if (isShuffle) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE
            )
        }
    }

    fun updateMetadata(
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        coverUri: String
    ) {
        val metaBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, coverUri)
            .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, coverUri)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, coverUri)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)

        mediaSessionCompat?.let { session ->
            session.isActive = true
            session.setMetadata(metaBuilder.build())
        }
    }

    fun release() {
        mediaSessionCompat?.isActive = false
        mediaSessionCompat?.release()
        mediaSessionCompat = null
    }
}

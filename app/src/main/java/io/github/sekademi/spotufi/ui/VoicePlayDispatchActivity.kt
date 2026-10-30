package io.github.sekademi.spotufi.ui

import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.ContextCompat
import io.github.sekademi.spotufi.di.SongPlayer
import io.github.sekademi.spotufi.ui.notification.MediaSessionHolder
import io.github.sekademi.spotufi.ui.notification.PlaybackService

/**
 * Headless trampoline activity for voice searches and media transport commands from
 * Google Assistant, Gemini, App Actions, and Android Auto.
 *
 * Runs with [android.R.style.Theme_NoDisplay] so no UI window is ever drawn.
 * Immediately dispatches the playback intent to [PlaybackService], updates the shared
 * [MediaSessionHolder] to [PlaybackStateCompat.STATE_BUFFERING] so Google Assistant
 * receives an instant acknowledgment, and calls [finish] immediately.
 * This completely prevents Android from treating Atify as a full-screen Driver Assistance UI
 * or trapping the user in a "Connecting to Driving Assistant" screen.
 */
class VoicePlayDispatchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dispatchVoiceIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dispatchVoiceIntent(intent)
        finish()
    }

    private fun dispatchVoiceIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action.orEmpty()
        when (action) {
            "io.github.sekademi.spotufi.action.PAUSE" -> {
                SongPlayer.pause()
                MediaSessionHolder.updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                forwardToService(intent)
                return
            }
            "io.github.sekademi.spotufi.action.RESUME" -> {
                SongPlayer.play()
                MediaSessionHolder.updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                forwardToService(intent)
                return
            }
        }

        val query = intent.getStringExtra(SearchManager.QUERY)
            ?: intent.getStringExtra("query")
            ?: intent.getStringExtra("media_query")
            ?: intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE)
            ?: intent.getStringExtra("feature")
            ?: intent.data?.getQueryParameter("query")
            ?: intent.data?.getQueryParameter("q")
            ?: listOfNotNull(
                intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST),
                intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM)
            ).joinToString(" ").takeIf { it.isNotBlank() }
            ?: ""

        // 1. Immediately ACK session state so Assistant knows playback has started
        MediaSessionHolder.getOrCreateSession(applicationContext)
        MediaSessionHolder.updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING)

        // 2. Dispatch to PlaybackService in foreground to resolve and stream audio
        val serviceIntent = Intent(applicationContext, PlaybackService::class.java).apply {
            this.action = MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH
            putExtra(SearchManager.QUERY, query)
            putExtra("query", query)
            intent.extras?.let { putExtras(it) }
        }
        forwardToService(serviceIntent)
    }

    private fun forwardToService(serviceIntent: Intent) {
        try {
            ContextCompat.startForegroundService(applicationContext, serviceIntent)
        } catch (_: Exception) {
            try {
                startService(serviceIntent)
            } catch (_: Exception) {}
        }
    }
}

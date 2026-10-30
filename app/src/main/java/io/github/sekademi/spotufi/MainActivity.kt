package io.github.sekademi.spotufi

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.enableEdgeToEdge
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.github.sekademi.spotufi.di.SongPlayer
import io.github.sekademi.spotufi.ui.notification.PlaybackService
import io.github.sekademi.spotufi.ui.theme.SpotuiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?){

        super.onCreate(savedInstanceState)
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        // Ask for notification permission (Android 13+) so the media notification shows.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Connect a controller to bootstrap the MediaSessionService: this brings up
        // the system media notification and keeps playback alive in the background.
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()


        // Ensure MediaSessionCompat is explicitly active and ready for assistant / media controls
        io.github.sekademi.spotufi.ui.notification.MediaSessionHolder.getOrCreateSession(this)
        enableEdgeToEdge()
        setContent {

            SpotuiTheme {
                // A surface container using the 'background' color from the theme
                    App()
            }
        }

        // Experimental Spotify web-player engine: attach its hidden WebView AFTER
        // setContent so the Compose content view doesn't replace/orphan it (an
        // orphaned WebView gets a 0×0 viewport and Spotify won't render/navigate).
        io.github.sekademi.spotufi.di.SpotifyWebPlayer.attach(this)

        handleDeepLink(intent)
        handleVoiceIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
        handleVoiceIntent(intent)
    }

    private fun handleVoiceIntent(intent: android.content.Intent?) {
        val action = intent?.action ?: return
        if (action == android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH ||
            action == "android.intent.action.MEDIA_SEARCH" ||
            action == "com.google.android.gms.actions.SEARCH_ACTION" ||
            action == "com.google.android.gms.actions.MEDIA_PLAY_FROM_SEARCH"
        ) {
            val serviceIntent = android.content.Intent(this, PlaybackService::class.java).apply {
                this.action = action
                intent.extras?.let { putExtras(it) }
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            if (intent.categories?.contains(android.content.Intent.CATEGORY_VOICE) == true) {
                moveTaskToBack(true)
            }
        } else if (action == android.content.Intent.ACTION_VIEW) {
            val query = intent.getStringExtra("query")
                ?: intent.getStringExtra("media_query")
                ?: intent.getStringExtra("feature")
                ?: intent.getStringExtra(android.app.SearchManager.QUERY)
            if (!query.isNullOrBlank()) {
                val serviceIntent = android.content.Intent(this, PlaybackService::class.java).apply {
                    this.action = android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH
                    putExtra(android.app.SearchManager.QUERY, query)
                    putExtra("query", query)
                }
                ContextCompat.startForegroundService(this, serviceIntent)
            }
        }
    }

    private fun handleDeepLink(intent: android.content.Intent?) {
        val data = intent?.data ?: return
        val host = data.host.orEmpty()
        if (host == "play" || host == "search") {
            val query = data.getQueryParameter("query")
                ?: data.getQueryParameter("q")
                ?: data.getQueryParameter("name")
                ?: data.getQueryParameter("feature")
                ?: ""
            val serviceIntent = android.content.Intent(this, PlaybackService::class.java).apply {
                action = android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH
                putExtra(android.app.SearchManager.QUERY, query)
                putExtra("query", query)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            return
        }

        val rawSpDc = data.getQueryParameter("sp_dc") ?: data.getQueryParameter("cookie")
        if (!rawSpDc.isNullOrBlank()) {
            val spDc = com.metrolist.spotify.CookieSanitizer.sanitizeSpDc(rawSpDc) ?: rawSpDc.trim()
            if (spDc.isNotBlank()) {
                io.github.sekademi.spotufi.data.api.SpotifySession.setSpDc(this, spDc)
                io.github.sekademi.spotufi.di.SpotifyWebPlayer.refreshLogin(this)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        io.github.sekademi.spotufi.di.SpotifyWebPlayer.detach(this)
    }
}




package io.github.sekademi.spotufi

import android.app.Application
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeLocale
import com.metrolist.music.utils.cipher.CipherDeobfuscator
import io.github.sekademi.spotufi.data.api.Api
import io.github.sekademi.spotufi.util.CrashLogger
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

@HiltAndroidApp
class MyApplication : Application(){
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        // For non-DI singletons (LyricsApi) that need a Context to refresh the token.
        @JvmStatic
        lateinit var instance: MyApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        CrashLogger.install(this)

        // Surface Spotify REST/GQL logs to logcat for diagnosis.
        com.metrolist.spotify.Spotify.logger = { level, msg ->
            Timber.tag("SpotifyREST").d("[$level] $msg")
        }
        com.metrolist.spotify.SpotifyCanvas.setLogger { level, msg ->
            Timber.tag("SpotifyCanvas").d("[$level] $msg")
        }
        // Required by the ported YouTube streaming flow (cipher/PoToken WebViews).
        CipherDeobfuscator.initialize(this)

        // Locale + visitorData must be set or the player can't mint a PoToken,
        // and googlevideo rejects the stream URL with HTTP 403 (tracks stuck at 0:00).
        val locale = Locale.getDefault()
        YouTube.locale = YouTubeLocale(
            gl = locale.country.takeIf { it.isNotBlank() } ?: "US",
            hl = locale.language.takeIf { it.isNotBlank() } ?: "en",
        )
        // YouTube playback runs anonymously; age-gated official audio falls back
        // to matching normal YouTube uploads instead of requiring sign-in.
        YouTube.cookie = null

        // Background initialization: run off the main thread to optimize cold start
        // and avoid spawning competing coroutines during app launch.
        appScope.launch(Dispatchers.IO) {
            runCatching {
                YouTube.visitorData = YouTube.visitorData().getOrNull() ?: YouTube.visitorData
            }

            // Warm the Home feed cache only if Spotify is authenticated.
            if (io.github.sekademi.spotufi.data.api.SpotifySession.spDc(this@MyApplication).isNotBlank()) {
                val api = Api(this@MyApplication)
                runCatching { api.getHomeFeed().collect {} }
                runCatching { api.getAlbums().collect {} }
                runCatching { api.getArtists().collect {} }
            }

            // Pre-warm the YouTube playback pipeline so the first tap doesn't pay for
            // cold-starting the player.js parser and BotGuard WebView (~2-4s combined).
            runCatching { com.metrolist.innertube.NewPipeExtractor.init() }
        }
    }
}

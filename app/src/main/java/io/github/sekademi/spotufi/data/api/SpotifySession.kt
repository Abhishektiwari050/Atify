package io.github.sekademi.spotufi.data.api

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import com.metrolist.spotify.Spotify
import io.github.sekademi.spotufi.data.preferences.clearPlaybackState
import io.github.sekademi.spotufi.di.SongPlayer
import io.github.sekademi.spotufi.di.SpotifyWebPlayer

/**
 * Stores the logged-in Spotify `sp_dc` cookie used to mint web access tokens.
 *
 * Set it once (e.g. from a settings screen) with [setSpDc]. To get yours: log in
 * at open.spotify.com, open devtools → Application → Cookies → copy `sp_dc`.
 */
object SpotifySession {
    private const val TAG = "SpotifySession"
    private const val PREFS = "spotify_session"
    private const val KEY_SP_DC = "sp_dc"

    // Optional compile-time default so the app has data on first launch.
    // Leave blank to require runtime configuration.
    private const val DEFAULT_SP_DC = ""

    fun spDc(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SP_DC, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_SP_DC
    }

    fun setSpDc(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SP_DC, value.trim())
            .apply()
    }

    /**
     * Completely logs the user out:
     * - Clears persisted `sp_dc` credential
     * - Resets in-memory access tokens and profile/metadata caches
     * - Clears saved playback restore points
     * - Purges all cookies and web storage from WebView
     * - Resets SpotifyWebPlayer and pauses audio playback
     */
    fun logout(context: Context) {
        setSpDc(context, "")
        Spotify.accessToken = null
        SpotifyTokenProvider.reset()
        ProfileCache.clear()
        Api.HomeCache.clear()
        clearPlaybackState(context)

        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.removeSessionCookies(null)
            cookieManager.flush()
            WebStorage.getInstance().deleteAllData()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cookies or web storage during logout", e)
        }

        SpotifyWebPlayer.reset()
        SongPlayer.pause()
    }
}

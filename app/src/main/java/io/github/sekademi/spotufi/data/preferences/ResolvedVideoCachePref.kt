package io.github.sekademi.spotufi.data.preferences

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

private const val PREF_RESOLVED_VIDEOS = "ResolvedVideosCache"
private const val PREF_SPOTIFY_VIDEO_MAPPING = "SpotifyVideoPermanentMap"

private val memoryVideoMapping = ConcurrentHashMap<String, String>()

fun getCachedVideoIds(context: Context, cacheKey: String): List<String>? {
    val raw = context.getSharedPreferences(PREF_RESOLVED_VIDEOS, Context.MODE_PRIVATE)
        .getString(cacheKey, null) ?: return null
    return raw.split(",").filter { it.isNotBlank() }
}

fun setCachedVideoIds(context: Context, cacheKey: String, videoIds: List<String>) {
    if (videoIds.isEmpty()) return
    val raw = videoIds.joinToString(",")
    context.getSharedPreferences(PREF_RESOLVED_VIDEOS, Context.MODE_PRIVATE)
        .edit()
        .putString(cacheKey, raw)
        .apply()
}

fun clearCachedVideoId(context: Context, songQuery: String) {
    val prefs = context.getSharedPreferences(PREF_RESOLVED_VIDEOS, Context.MODE_PRIVATE)
    // Clear both song and video search cache keys for this query
    prefs.edit()
        .remove("$songQuery|FILTER_SONG")
        .remove("$songQuery|FILTER_VIDEO")
        .apply()
}

/**
 * Retrieves the permanently mapped YouTube video ID for a Spotify track ID.
 * YouTube video IDs never change or expire, so caching this avoids future search calls.
 */
fun getPermanentVideoIdForSpotifyId(context: Context, spotifyId: String): String? {
    if (spotifyId.isBlank()) return null
    memoryVideoMapping[spotifyId]?.let { return it }
    val persisted = context.getSharedPreferences(PREF_SPOTIFY_VIDEO_MAPPING, Context.MODE_PRIVATE)
        .getString(spotifyId, null)
    if (!persisted.isNullOrBlank()) {
        memoryVideoMapping[spotifyId] = persisted
        return persisted
    }
    return null
}

/**
 * Permanently stores a verified YouTube video ID match for a Spotify track ID.
 */
fun setPermanentVideoIdForSpotifyId(context: Context, spotifyId: String, videoId: String) {
    if (spotifyId.isBlank() || videoId.isBlank()) return
    memoryVideoMapping[spotifyId] = videoId
    context.getSharedPreferences(PREF_SPOTIFY_VIDEO_MAPPING, Context.MODE_PRIVATE)
        .edit()
        .putString(spotifyId, videoId)
        .apply()
}

/**
 * Clears a permanently mapped video ID in case a YouTube video is removed or invalid.
 */
fun clearPermanentVideoIdForSpotifyId(context: Context, spotifyId: String) {
    if (spotifyId.isBlank()) return
    memoryVideoMapping.remove(spotifyId)
    context.getSharedPreferences(PREF_SPOTIFY_VIDEO_MAPPING, Context.MODE_PRIVATE)
        .edit()
        .remove(spotifyId)
        .apply()
}

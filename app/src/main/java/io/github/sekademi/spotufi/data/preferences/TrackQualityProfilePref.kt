package io.github.sekademi.spotufi.data.preferences

import android.content.Context

private const val PREF_TRACK_QUALITY_PROFILE = "TrackQualityProfile"
private const val SUFFIX_BEST_SOURCE = "_bsrc"
private const val SUFFIX_BEST_QUALITY = "_bq"
private const val SUFFIX_LOSSLESS_STATUS = "_ll_status"
private const val SUFFIX_BEST_VIDEO_ID = "_bvid"
private const val SUFFIX_TIMESTAMP = "_ts"

/** Max age for negative (UNAVAILABLE) cache = 3 days so newly added lossless tracks can eventually be re-checked */
private const val UNAVAILABLE_CACHE_TTL_MS = 3 * 24 * 60 * 60 * 1000L

enum class LosslessAvailability {
    UNKNOWN,
    AVAILABLE,
    UNAVAILABLE,
}

data class TrackQualityProfile(
    val query: String,
    val bestSource: String,
    val bestQuality: String,
    val losslessAvailability: LosslessAvailability,
    val bestVideoId: String?,
    val timestamp: Long,
)

fun getTrackQualityProfile(context: Context, query: String): TrackQualityProfile? {
    val prefs = context.getSharedPreferences(PREF_TRACK_QUALITY_PROFILE, Context.MODE_PRIVATE)
    val bestSource = prefs.getString(query + SUFFIX_BEST_SOURCE, null) ?: return null
    val bestQuality = prefs.getString(query + SUFFIX_BEST_QUALITY, "") ?: ""
    val rawStatus = prefs.getString(query + SUFFIX_LOSSLESS_STATUS, LosslessAvailability.UNKNOWN.name)
    val status = runCatching { LosslessAvailability.valueOf(rawStatus!!) }.getOrDefault(LosslessAvailability.UNKNOWN)
    val bestVideoId = prefs.getString(query + SUFFIX_BEST_VIDEO_ID, null)
    val timestamp = prefs.getLong(query + SUFFIX_TIMESTAMP, 0L)

    // Expire negative cache after 3 days to allow fresh check
    if (status == LosslessAvailability.UNAVAILABLE && System.currentTimeMillis() - timestamp > UNAVAILABLE_CACHE_TTL_MS) {
        return null
    }

    return TrackQualityProfile(
        query = query,
        bestSource = bestSource,
        bestQuality = bestQuality,
        losslessAvailability = status,
        bestVideoId = bestVideoId,
        timestamp = timestamp,
    )
}

fun setTrackQualityProfile(
    context: Context,
    query: String,
    bestSource: String,
    bestQuality: String,
    losslessAvailability: LosslessAvailability,
    bestVideoId: String? = null,
) {
    if (query.isBlank()) return
    val editor = context.getSharedPreferences(PREF_TRACK_QUALITY_PROFILE, Context.MODE_PRIVATE)
        .edit()
        .putString(query + SUFFIX_BEST_SOURCE, bestSource)
        .putString(query + SUFFIX_BEST_QUALITY, bestQuality)
        .putString(query + SUFFIX_LOSSLESS_STATUS, losslessAvailability.name)
        .putLong(query + SUFFIX_TIMESTAMP, System.currentTimeMillis())

    if (bestVideoId != null) {
        editor.putString(query + SUFFIX_BEST_VIDEO_ID, bestVideoId)
    }
    editor.apply()
}

fun flagLosslessUnavailable(context: Context, query: String) {
    if (query.isBlank()) return
    val existing = getTrackQualityProfile(context, query)
    setTrackQualityProfile(
        context = context,
        query = query,
        bestSource = existing?.bestSource ?: "YouTube",
        bestQuality = existing?.bestQuality ?: "",
        losslessAvailability = LosslessAvailability.UNAVAILABLE,
        bestVideoId = existing?.bestVideoId,
    )
}

fun flagLosslessAvailable(context: Context, query: String, provider: String, quality: String) {
    if (query.isBlank()) return
    val existing = getTrackQualityProfile(context, query)
    setTrackQualityProfile(
        context = context,
        query = query,
        bestSource = "Lossless • $provider",
        bestQuality = quality,
        losslessAvailability = LosslessAvailability.AVAILABLE,
        bestVideoId = existing?.bestVideoId,
    )
}

fun flagBestVideoMatch(context: Context, query: String, videoId: String, quality: String) {
    if (query.isBlank() || videoId.isBlank()) return
    val existing = getTrackQualityProfile(context, query)
    setTrackQualityProfile(
        context = context,
        query = query,
        bestSource = existing?.bestSource ?: "YouTube",
        bestQuality = if (existing?.losslessAvailability == LosslessAvailability.AVAILABLE) existing.bestQuality else quality,
        losslessAvailability = existing?.losslessAvailability ?: LosslessAvailability.UNKNOWN,
        bestVideoId = videoId,
    )
}

fun clearTrackQualityProfile(context: Context, query: String) {
    context.getSharedPreferences(PREF_TRACK_QUALITY_PROFILE, Context.MODE_PRIVATE)
        .edit()
        .remove(query + SUFFIX_BEST_SOURCE)
        .remove(query + SUFFIX_BEST_QUALITY)
        .remove(query + SUFFIX_LOSSLESS_STATUS)
        .remove(query + SUFFIX_BEST_VIDEO_ID)
        .remove(query + SUFFIX_TIMESTAMP)
        .apply()
}

package io.github.sekademi.spotufi.data.preferences

import android.content.Context
import io.github.sekademi.spotufi.data.recommendation.UserTasteProfile
import org.json.JSONObject

private const val PREF_NAME = "UserTasteProfile"
private const val KEY_PROFILE = "profile_json"

fun getUserTasteProfile(context: Context): UserTasteProfile {
    val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_PROFILE, null) ?: return UserTasteProfile()
    return runCatching {
        val root = JSONObject(raw)
        val artistJson = root.optJSONObject("artists") ?: JSONObject()
        val genreJson = root.optJSONObject("genres") ?: JSONObject()
        val penaltyJson = root.optJSONObject("penalties") ?: JSONObject()
        val lastUpdated = root.optLong("lastUpdated", System.currentTimeMillis())

        val artists = mutableMapOf<String, Float>()
        artistJson.keys().forEach { k ->
            artists[k] = artistJson.optDouble(k, 0.0).toFloat()
        }

        val genres = mutableMapOf<String, Float>()
        genreJson.keys().forEach { k ->
            genres[k] = genreJson.optDouble(k, 0.0).toFloat()
        }

        val penalties = mutableMapOf<String, Float>()
        penaltyJson.keys().forEach { k ->
            penalties[k] = penaltyJson.optDouble(k, 0.0).toFloat()
        }

        UserTasteProfile(
            artistAffinities = artists,
            genreWeights = genres,
            negativeArtistPenalties = penalties,
            lastUpdated = lastUpdated,
        )
    }.getOrDefault(UserTasteProfile())
}

fun saveUserTasteProfile(context: Context, profile: UserTasteProfile) {
    val root = JSONObject()
    val artistJson = JSONObject()
    profile.artistAffinities.forEach { (k, v) -> artistJson.put(k, v.toDouble()) }
    root.put("artists", artistJson)

    val genreJson = JSONObject()
    profile.genreWeights.forEach { (k, v) -> genreJson.put(k, v.toDouble()) }
    root.put("genres", genreJson)

    val penaltyJson = JSONObject()
    profile.negativeArtistPenalties.forEach { (k, v) -> penaltyJson.put(k, v.toDouble()) }
    root.put("penalties", penaltyJson)

    root.put("lastUpdated", profile.lastUpdated)

    context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_PROFILE, root.toString())
        .apply()
}

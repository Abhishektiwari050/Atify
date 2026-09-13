package io.github.sekademi.spotufi.data.recommendation

import android.content.Context
import android.util.Log
import io.github.sekademi.spotufi.data.preferences.getUserTasteProfile
import io.github.sekademi.spotufi.data.preferences.saveUserTasteProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.exp

/**
 * On-device Machine Learning taste profile engine.
 *
 * Processes implicit and explicit user feedback (completions, likes, skips, replays),
 * maintains time-decayed sparse taste feature vectors, and computes cosine similarity
 * scores for candidates.
 */
object TasteProfileEngine {

    private const val TAG = "TasteProfileEngine"
    private const val HALF_LIFE_MS = 14L * 24 * 60 * 60 * 1000 // 14-day exponential half-life
    private val LN2 = kotlin.math.ln(2.0)

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cachedProfile: UserTasteProfile? = null

    /**
     * Retrieves the current user taste vector, applying time-decay since the last update.
     */
    fun getProfile(context: Context): UserTasteProfile {
        val existing = cachedProfile ?: synchronized(this) {
            cachedProfile ?: getUserTasteProfile(context).also { cachedProfile = it }
        }

        val elapsed = System.currentTimeMillis() - existing.lastUpdated
        if (elapsed > 60L * 60 * 1000) { // Apply decay if >1 hour has elapsed
            val decayFactor = exp(-LN2 * (elapsed.toDouble() / HALF_LIFE_MS.toDouble())).toFloat()
            if (decayFactor < 0.99f) {
                val decayed = existing.decayed(decayFactor)
                cachedProfile = decayed
                engineScope.launch {
                    saveUserTasteProfile(context, decayed)
                }
                return decayed
            }
        }
        return existing
    }

    /**
     * Record a completed listen (>= 80% duration).
     * Awards +1.0 positive weight to artist and +0.5 to associated genres.
     */
    fun recordCompletion(
        context: Context,
        artistIds: Collection<String>,
        genres: Collection<String> = emptyList(),
    ) {
        if (artistIds.isEmpty()) return
        engineScope.launch {
            val current = getProfile(context)
            val updatedArtists = current.artistAffinities.toMutableMap()
            for (id in artistIds) {
                if (id.isNotBlank()) {
                    updatedArtists[id] = (updatedArtists[id] ?: 0f) + 1.0f
                }
            }

            val updatedGenres = current.genreWeights.toMutableMap()
            for (g in genres) {
                val key = g.lowercase().trim()
                if (key.isNotBlank()) {
                    updatedGenres[key] = (updatedGenres[key] ?: 0f) + 0.5f
                }
            }

            // Reward reduces penalty if user previously skipped this artist
            val updatedPenalties = current.negativeArtistPenalties.toMutableMap()
            for (id in artistIds) {
                val existingPenalty = updatedPenalties[id] ?: 0f
                if (existingPenalty > 0f) {
                    updatedPenalties[id] = (existingPenalty - 0.5f).coerceAtLeast(0f)
                }
            }

            val updated = current.copy(
                artistAffinities = updatedArtists,
                genreWeights = updatedGenres,
                negativeArtistPenalties = updatedPenalties.filterValues { it > 0.01f },
                lastUpdated = System.currentTimeMillis(),
            )
            cachedProfile = updated
            saveUserTasteProfile(context, updated)
            Log.d(TAG, "Recorded completion for artists: $artistIds")
        }
    }

    /**
     * Record an explicit like (heart tapped).
     * Awards +3.0 boost to artist and +1.5 to associated genres.
     */
    fun recordLike(
        context: Context,
        artistIds: Collection<String>,
        genres: Collection<String> = emptyList(),
    ) {
        if (artistIds.isEmpty()) return
        engineScope.launch {
            val current = getProfile(context)
            val updatedArtists = current.artistAffinities.toMutableMap()
            for (id in artistIds) {
                if (id.isNotBlank()) {
                    updatedArtists[id] = (updatedArtists[id] ?: 0f) + 3.0f
                }
            }

            val updatedGenres = current.genreWeights.toMutableMap()
            for (g in genres) {
                val key = g.lowercase().trim()
                if (key.isNotBlank()) {
                    updatedGenres[key] = (updatedGenres[key] ?: 0f) + 1.5f
                }
            }

            val updated = current.copy(
                artistAffinities = updatedArtists,
                genreWeights = updatedGenres,
                lastUpdated = System.currentTimeMillis(),
            )
            cachedProfile = updated
            saveUserTasteProfile(context, updated)
            Log.d(TAG, "Recorded like for artists: $artistIds")
        }
    }

    /**
     * Record an early skip (<25 seconds).
     * Adds +1.5 penalty to artist and dampens associated genres.
     */
    fun recordSkip(
        context: Context,
        artistIds: Collection<String>,
        genres: Collection<String> = emptyList(),
    ) {
        if (artistIds.isEmpty()) return
        engineScope.launch {
            val current = getProfile(context)
            val updatedPenalties = current.negativeArtistPenalties.toMutableMap()
            for (id in artistIds) {
                if (id.isNotBlank()) {
                    updatedPenalties[id] = (updatedPenalties[id] ?: 0f) + 1.5f
                }
            }

            // Dampen genres slightly
            val updatedGenres = current.genreWeights.toMutableMap()
            for (g in genres) {
                val key = g.lowercase().trim()
                if (key.isNotBlank()) {
                    val currentWeight = updatedGenres[key] ?: 0f
                    updatedGenres[key] = (currentWeight - 0.25f).coerceAtLeast(0f)
                }
            }

            val updated = current.copy(
                genreWeights = updatedGenres.filterValues { it > 0.01f },
                negativeArtistPenalties = updatedPenalties,
                lastUpdated = System.currentTimeMillis(),
            )
            cachedProfile = updated
            saveUserTasteProfile(context, updated)
            Log.d(TAG, "Recorded skip penalty for artists: $artistIds")
        }
    }

    /**
     * Scores a candidate track against the active user taste vector using Cosine Similarity.
     */
    fun scoreCandidate(
        context: Context,
        trackArtistIds: Collection<String>,
        trackGenres: Collection<String> = emptyList(),
    ): Float {
        val profile = getProfile(context)
        return profile.scoreCandidate(trackArtistIds, trackGenres)
    }
}

package io.github.sekademi.spotufi.data.recommendation

import kotlin.math.sqrt

/**
 * Represents the user's multi-dimensional dynamic taste vector.
 *
 * Encapsulates:
 * - [artistAffinities]: Artist ID -> accumulated positive weight (plays, completions, likes).
 * - [genreWeights]: Normalized TF-IDF genre weight (e.g. "indie rock" -> 0.85).
 * - [negativeArtistPenalties]: Artist ID -> penalty accumulated from rapid skips (<25s).
 * - [lastUpdated]: Epoch timestamp of the last update.
 */
data class UserTasteProfile(
    val artistAffinities: Map<String, Float> = emptyMap(),
    val genreWeights: Map<String, Float> = emptyMap(),
    val negativeArtistPenalties: Map<String, Float> = emptyMap(),
    val lastUpdated: Long = System.currentTimeMillis(),
) {

    /**
     * Computes the vector norm (magnitude) of the positive taste profile.
     */
    val norm: Float by lazy {
        var sumSquares = 0f
        for (v in artistAffinities.values) {
            sumSquares += v * v
        }
        for (v in genreWeights.values) {
            sumSquares += v * v
        }
        sqrt(sumSquares).coerceAtLeast(0.0001f)
    }

    /**
     * Calculates the Cosine Similarity between this user taste vector and a candidate track.
     *
     * @param trackArtistIds The artist IDs credited on the track.
     * @param trackGenres The genres associated with the track's artists.
     * @return Normalized similarity in [0.0, 1.0], penalized by negative skip history.
     */
    fun scoreCandidate(trackArtistIds: Collection<String>, trackGenres: Collection<String>): Float {
        if (artistAffinities.isEmpty() && genreWeights.isEmpty()) {
            return 0.5f // Neutral baseline when profile is empty
        }

        // Check if track artists have high negative skip penalties
        val maxPenalty = trackArtistIds.maxOfOrNull { negativeArtistPenalties[it] ?: 0f } ?: 0f
        if (maxPenalty >= 3.0f) {
            // Highly disliked artist: strongly downrank or suppress
            return 0.05f
        }

        // Compute candidate track vector norm
        // Artists have weight 1.0 each; genres are scaled by inverse square root to prevent genre bloat
        val genreWeightPerItem = if (trackGenres.isNotEmpty()) 1.0f / sqrt(trackGenres.size.toFloat()) else 0f
        val candidateNormSq = trackArtistIds.size * 1.0f + (if (trackGenres.isNotEmpty()) 1.0f else 0f)
        val candidateNorm = sqrt(candidateNormSq).coerceAtLeast(0.0001f)

        // Dot product
        var dotProduct = 0f
        for (artistId in trackArtistIds) {
            dotProduct += (artistAffinities[artistId] ?: 0f) * 1.0f
        }
        for (genre in trackGenres) {
            dotProduct += (genreWeights[genre.lowercase()] ?: 0f) * genreWeightPerItem
        }

        val rawCosine = (dotProduct / (norm * candidateNorm)).coerceIn(0f, 1f)

        // Apply negative skip penalty discount
        val penaltyDiscount = (1.0f - (maxPenalty * 0.25f)).coerceIn(0.1f, 1.0f)
        return rawCosine * penaltyDiscount
    }

    /**
     * Decays older interactions by an exponential half-life factor (e.g. 14 days).
     */
    fun decayed(decayFactor: Float): UserTasteProfile {
        if (decayFactor >= 0.999f) return this
        val decayedArtists = artistAffinities.mapValues { it.value * decayFactor }.filterValues { it > 0.01f }
        val decayedGenres = genreWeights.mapValues { it.value * decayFactor }.filterValues { it > 0.01f }
        val decayedPenalties = negativeArtistPenalties.mapValues { it.value * decayFactor }.filterValues { it > 0.01f }
        return copy(
            artistAffinities = decayedArtists,
            genreWeights = decayedGenres,
            negativeArtistPenalties = decayedPenalties,
            lastUpdated = System.currentTimeMillis(),
        )
    }
}

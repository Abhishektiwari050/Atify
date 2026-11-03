package io.github.sekademi.spotufi.data.recommendation

import android.content.Context
import io.github.sekademi.spotufi.data.entity.SongsModel
import kotlin.math.abs

/**
 * Provides on-device ML reranking for Search results and playlists.
 *
 * - [rerankSearchResults]: Boosts relevant search results based on the user's personal taste vector.
 * - [sortSmartFlow]: Arranges a list of tracks into a cohesive "musical flow" using greedy
 *   nearest-neighbor trajectory over artist affinities and genres.
 */
object SmartMusicRanker {

    /**
     * Reranks raw search results to elevate tracks by artists or genres the user loves,
     * while preserving high textual relevance for explicit queries.
     */
    fun rerankSearchResults(
        context: Context,
        results: List<SongsModel>,
        query: String,
    ): List<SongsModel> {
        if (results.size <= 2 || query.isBlank()) return results

        fun cleanForMatching(text: String): String =
            text.lowercase()
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

        val cleanQuery = cleanForMatching(query)
        if (cleanQuery.isBlank()) return results

        val queryTokens = cleanQuery.split(" ").filter { it.length > 1 }
        val unspacedQuery = cleanQuery.replace(" ", "")

        return results.mapIndexed { index, song ->
            val cleanTitle = cleanForMatching(song.title)
            val cleanSinger = cleanForMatching(song.singer)
            val cleanAlbum = cleanForMatching(song.album)
            val combinedText = "$cleanTitle $cleanSinger $cleanAlbum"
            val unspacedTitle = cleanTitle.replace(" ", "")

            // 1. Exact & direct phrase matches
            val exactTitle = if (cleanTitle == cleanQuery) 1.0f else 0.0f
            val startsWithTitle = if (cleanTitle.startsWith(cleanQuery)) 0.90f else 0.0f
            val exactSinger = if (cleanSinger == cleanQuery) 0.88f else 0.0f
            val startsWithSinger = if (cleanSinger.startsWith(cleanQuery)) 0.82f else 0.0f
            val directPhrase = if (combinedText.contains(cleanQuery)) 0.80f else 0.0f
            val unspacedMatch = if (unspacedTitle.contains(unspacedQuery)) 0.85f else 0.0f

            // 2. Token-level coverage (handles "artist song", "song artist", words out of order)
            val tokenMatchRatio = if (queryTokens.isNotEmpty()) {
                val matchedCount = queryTokens.count { token ->
                    combinedText.contains(token) || unspacedTitle.contains(token)
                }
                matchedCount.toFloat() / queryTokens.size.toFloat()
            } else 0f
            val tokenScore = when {
                tokenMatchRatio >= 0.99f -> 0.86f
                tokenMatchRatio >= 0.75f -> 0.70f
                tokenMatchRatio >= 0.50f -> 0.50f
                else -> 0.15f
            }

            // High text relevance is dominant
            val textRelevance = maxOf(
                exactTitle,
                startsWithTitle,
                unspacedMatch,
                tokenScore,
                exactSinger,
                startsWithSinger,
                directPhrase,
                0.15f,
            )

            // ML Taste affinity score (0.0 to 1.0)
            val artistIds = song.artistIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
                .ifEmpty { song.singer.split(",", "&", "/").map { it.trim() }.filter { it.isNotBlank() } }
            val tasteScore = TasteProfileEngine.scoreCandidate(context, artistIds)

            // Original search rank position prior
            val positionPrior = 1.0f - (index.toFloat() / results.size.toFloat()) * 0.3f

            // Final composite rank: text relevance is strongly weighted (0.70) so users find their song
            val compositeScore = (textRelevance * 0.70f) + (tasteScore * 0.18f) + (positionPrior * 0.12f)
            Pair(song, compositeScore)
        }.sortedByDescending { it.second }.map { it.first }
    }

    /**
     * "Smart Flow" playlist sorting: orders songs into an optimal listening trajectory
     * using a greedy nearest-neighbor path across artist affinities and durations.
     * Prevents jarring jumps and creates a smoothly curated session.
     */
    fun sortSmartFlow(context: Context, songs: List<SongsModel>): List<SongsModel> {
        if (songs.size <= 2) return songs

        val remaining = songs.toMutableList()
        val flow = mutableListOf<SongsModel>()

        // Seed with the highest personal affinity track
        val profile = TasteProfileEngine.getProfile(context)
        var current = remaining.maxByOrNull { song ->
            val artists = song.artistIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
            artists.maxOfOrNull { profile.artistAffinities[it] ?: 0f } ?: 0f
        } ?: remaining.first()

        flow.add(current)
        remaining.remove(current)

        while (remaining.isNotEmpty()) {
            val currentArtists = current.artistIds.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                .ifEmpty { current.singer.split(",", "&", "/").map { it.trim() }.filter { it.isNotBlank() }.toSet() }

            // Find the remaining track that has the closest musical affinity
            val nextSong = remaining.maxByOrNull { candidate ->
                val candArtists = candidate.artistIds.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                    .ifEmpty { candidate.singer.split(",", "&", "/").map { it.trim() }.filter { it.isNotBlank() }.toSet() }

                val sameArtist = if (currentArtists.intersect(candArtists).isNotEmpty()) 1.0f else 0.0f
                val tasteScore = TasteProfileEngine.scoreCandidate(context, candArtists)

                // Temporal pacing: tracks with similar durations flow better
                val durationDiffSec = abs(candidate.durationMs - current.durationMs) / 1000
                val durationPacing = (1.0f - (durationDiffSec.toFloat() / 300f)).coerceIn(0f, 1f)

                (sameArtist * 0.40f) + (tasteScore * 0.40f) + (durationPacing * 0.20f)
            } ?: remaining.first()

            flow.add(nextSong)
            remaining.remove(nextSong)
            current = nextSong
        }

        return flow
    }
}

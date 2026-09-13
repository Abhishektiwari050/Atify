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

        val normalizedQuery = query.trim().lowercase()

        return results.mapIndexed { index, song ->
            val titleLower = song.title.lowercase()
            val singerLower = song.singer.lowercase()

            // Textual relevance score (0.0 to 1.0)
            val exactTitle = if (titleLower == normalizedQuery) 1.0f else 0.0f
            val startsWithTitle = if (titleLower.startsWith(normalizedQuery)) 0.8f else 0.0f
            val containsTitle = if (titleLower.contains(normalizedQuery)) 0.6f else 0.0f
            val containsSinger = if (singerLower.contains(normalizedQuery)) 0.7f else 0.0f
            val textRelevance = maxOf(exactTitle, startsWithTitle, containsTitle, containsSinger, 0.2f)

            // ML Taste affinity score (0.0 to 1.0)
            val artistIds = song.artistIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
                .ifEmpty { song.singer.split(",", "&", "/").map { it.trim() }.filter { it.isNotBlank() } }
            val tasteScore = TasteProfileEngine.scoreCandidate(context, artistIds)

            // Original search rank position decay (top results from Spotify get a position prior)
            val positionPrior = 1.0f - (index.toFloat() / results.size.toFloat()) * 0.3f

            // Final composite rank score
            val compositeScore = (textRelevance * 0.45f) + (tasteScore * 0.40f) + (positionPrior * 0.15f)
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

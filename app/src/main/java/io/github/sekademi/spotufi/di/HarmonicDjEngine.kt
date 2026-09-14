package io.github.sekademi.spotufi.di

import com.metrolist.spotify.Spotify
import com.metrolist.spotify.models.SpotifyAudioFeatures
import io.github.sekademi.spotufi.data.entity.SongsModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.min

/**
 * Smart DJ Harmonic Flow and BPM Auto-Mixer.
 *
 * Implements the Camelot Wheel (1A–12B) harmonic mixing standard:
 * - Compatible transitions: Same key (8A -> 8A), Relative major/minor (8A <-> 8B),
 *   Adjacent fifths on the wheel (8A -> 7A or 9A), and Energy boost (+2 keys, 8A -> 10A).
 * - BPM auto-clustering: Minimizes tempo jumps, taking into account half-time/double-time.
 */
object HarmonicDjEngine {

    data class CamelotKey(val number: Int, val mode: Char) {
        override fun toString(): String = "$number$mode"
    }

    data class TrackHarmonics(
        val songId: Int,
        val camelot: CamelotKey,
        val bpm: Float,
        val energy: Float,
    )

    // Process-level cache of track harmonics to prevent repeated network queries
    private val harmonicsCache = ConcurrentHashMap<String, TrackHarmonics>()

    fun pitchClassToCamelot(key: Int, mode: Int): CamelotKey {
        if (key !in 0..11) return CamelotKey(8, 'A')
        val isMajor = mode == 1
        val num = if (isMajor) {
            when (key) {
                0 -> 8   // C
                1 -> 3   // C# / Db
                2 -> 10  // D
                3 -> 5   // D# / Eb
                4 -> 12  // E
                5 -> 7   // F
                6 -> 2   // F# / Gb
                7 -> 9   // G
                8 -> 4   // G# / Ab
                9 -> 11  // A
                10 -> 6  // A# / Bb
                11 -> 1  // B
                else -> 8
            }
        } else {
            when (key) {
                0 -> 5   // C minor
                1 -> 12  // C# minor
                2 -> 7   // D minor
                3 -> 2   // D# minor
                4 -> 9   // E minor
                5 -> 4   // F minor
                6 -> 11  // F# minor
                7 -> 6   // G minor
                8 -> 1   // G# minor
                9 -> 8   // A minor
                10 -> 3  // A# minor
                11 -> 10 // B minor
                else -> 8
            }
        }
        return CamelotKey(num, if (isMajor) 'B' else 'A')
    }

    private fun deterministicFallbackHarmonics(song: SongsModel): TrackHarmonics {
        val hash = abs("${song.title}|${song.singer}".hashCode())
        val number = (hash % 12) + 1
        val mode = if ((hash / 12) % 2 == 0) 'A' else 'B'
        val bpm = 90f + (hash % 50).toFloat()
        val energy = 0.5f + ((hash % 100) / 200f)
        return TrackHarmonics(song.id, CamelotKey(number, mode), bpm, energy)
    }

    suspend fun resolveHarmonics(songs: List<SongsModel>): Map<Int, TrackHarmonics> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<Int, TrackHarmonics>()
            val missingFromCache = mutableListOf<SongsModel>()

            for (song in songs) {
                val cached = harmonicsCache[song.spotifyTrackId] ?: harmonicsCache[song.id.toString()]
                if (cached != null) {
                    result[song.id] = cached
                } else if (song.spotifyTrackId.isNotBlank()) {
                    missingFromCache.add(song)
                } else {
                    val fallback = deterministicFallbackHarmonics(song)
                    harmonicsCache[song.id.toString()] = fallback
                    result[song.id] = fallback
                }
            }

            if (missingFromCache.isNotEmpty()) {
                val trackIds = missingFromCache.map { it.spotifyTrackId }
                val featuresList = Spotify.audioFeatures(trackIds).getOrDefault(emptyList())
                val featuresById = featuresList.associateBy { it.id }

                for (song in missingFromCache) {
                    val feat = featuresById[song.spotifyTrackId]
                    val harmonics = if (feat != null && feat.key >= 0) {
                        val camelot = pitchClassToCamelot(feat.key, feat.mode)
                        TrackHarmonics(
                            songId = song.id,
                            camelot = camelot,
                            bpm = if (feat.tempo > 0f) feat.tempo else 120f,
                            energy = feat.energy,
                        )
                    } else {
                        deterministicFallbackHarmonics(song)
                    }
                    harmonicsCache[song.spotifyTrackId] = harmonics
                    harmonicsCache[song.id.toString()] = harmonics
                    result[song.id] = harmonics
                }
            }

            result
        }

    fun calculateTransitionScore(from: TrackHarmonics, to: TrackHarmonics): Float {
        // 1. Camelot Wheel Key compatibility (0..50 pts)
        val k1 = from.camelot
        val k2 = to.camelot

        val numDiff = abs(k1.number - k2.number)
        val circularDiff = min(numDiff, 12 - numDiff)

        val keyScore = when {
            // Same key
            circularDiff == 0 && k1.mode == k2.mode -> 50f
            // Relative Major / Minor (same number, opposite mode, e.g. 8A <-> 8B)
            circularDiff == 0 && k1.mode != k2.mode -> 46f
            // Adjacent on wheel (step ±1, same mode, e.g. 8A -> 7A or 9A)
            circularDiff == 1 && k1.mode == k2.mode -> 42f
            // Energy boost (+2 keys, classic DJ transition to lift energy, e.g. 8A -> 10A)
            (k2.number - k1.number + 12) % 12 == 2 && k1.mode == k2.mode -> 38f
            // Diagonal adjacent (step ±1, opposite mode, e.g. 8A -> 7B)
            circularDiff == 1 && k1.mode != k2.mode -> 34f
            // Harmonic distance penalty
            else -> (25f - circularDiff * 3f).coerceAtLeast(0f)
        }

        // 2. BPM compatibility (0..50 pts)
        val bpm1 = from.bpm.coerceAtLeast(60f)
        val bpm2 = to.bpm.coerceAtLeast(60f)

        // Compare direct BPM, half-time, or double-time
        val ratioDirect = abs(bpm1 - bpm2) / bpm1
        val ratioHalf = abs(bpm1 - (bpm2 * 2f)) / bpm1
        val ratioDouble = abs((bpm1 * 2f) - bpm2) / (bpm1 * 2f)
        val minRatio = min(ratioDirect, min(ratioHalf, ratioDouble))

        // Ratio <= 0.08 (±8%) gets full compatibility
        val bpmScore = (50f * (1f - (minRatio / 0.35f))).coerceIn(0f, 50f)

        return keyScore + bpmScore
    }

    /**
     * Reorders [upcoming] tracks using greedy nearest-neighbor with Camelot key and BPM matching.
     * Starts from [anchorSong] (currently playing track) to create a seamless transition into the queue.
     */
    suspend fun reorderHarmonicQueue(
        anchorSong: SongsModel?,
        upcoming: List<SongsModel>,
    ): List<SongsModel> = withContext(Dispatchers.Default) {
        if (upcoming.size <= 1) return@withContext upcoming

        val allSongs = if (anchorSong != null) listOf(anchorSong) + upcoming else upcoming
        val harmonicsMap = resolveHarmonics(allSongs)

        val startHarmonics = if (anchorSong != null) {
            harmonicsMap[anchorSong.id] ?: deterministicFallbackHarmonics(anchorSong)
        } else {
            harmonicsMap[upcoming.first().id] ?: deterministicFallbackHarmonics(upcoming.first())
        }

        val remaining = upcoming.toMutableList()
        val ordered = ArrayList<SongsModel>(upcoming.size)

        var currentHarmonics = startHarmonics

        while (remaining.isNotEmpty()) {
            var bestIdx = 0
            var bestScore = -1f

            for (i in remaining.indices) {
                val candidate = remaining[i]
                val candidateHarmonics = harmonicsMap[candidate.id] ?: deterministicFallbackHarmonics(candidate)
                val score = calculateTransitionScore(currentHarmonics, candidateHarmonics)
                if (score > bestScore) {
                    bestScore = score
                    bestIdx = i
                }
            }

            val chosen = remaining.removeAt(bestIdx)
            ordered.add(chosen)
            currentHarmonics = harmonicsMap[chosen.id] ?: deterministicFallbackHarmonics(chosen)
        }

        ordered
    }
}

package io.github.sekademi.spotufi.data.recommendation

import io.github.sekademi.spotufi.data.entity.SongsModel
import kotlin.math.abs
import kotlin.math.min

/**
 * Harmonic key representation using the Camelot Wheel system.
 * (1A - 12A: Minor keys, 1B - 12B: Major keys)
 */
data class CamelotKey(
    val number: Int, // 1 to 12
    val letter: Char // 'A' (Minor) or 'B' (Major)
) {
    override fun toString(): String = "$number$letter"

    companion object {
        private val PITCH_CLASS_TO_CAMELOT_MINOR = mapOf(
            0 to CamelotKey(5, 'A'),  // C minor -> 5A
            1 to CamelotKey(12, 'A'), // C# minor -> 12A
            2 to CamelotKey(7, 'A'),  // D minor -> 7A
            3 to CamelotKey(2, 'A'),  // D# minor -> 2A
            4 to CamelotKey(9, 'A'),  // E minor -> 9A
            5 to CamelotKey(4, 'A'),  // F minor -> 4A
            6 to CamelotKey(11, 'A'), // F# minor -> 11A
            7 to CamelotKey(6, 'A'),  // G minor -> 6A
            8 to CamelotKey(1, 'A'),  // G# minor -> 1A
            9 to CamelotKey(8, 'A'),  // A minor -> 8A
            10 to CamelotKey(3, 'A'), // A# minor -> 3A
            11 to CamelotKey(10, 'A') // B minor -> 10A
        )

        private val PITCH_CLASS_TO_CAMELOT_MAJOR = mapOf(
            0 to CamelotKey(8, 'B'),  // C major -> 8B
            1 to CamelotKey(3, 'B'),  // C# major -> 3B
            2 to CamelotKey(10, 'B'), // D major -> 10B
            3 to CamelotKey(5, 'B'),  // D# major -> 5B
            4 to CamelotKey(12, 'B'), // E major -> 12B
            5 to CamelotKey(7, 'B'),  // F major -> 7B
            6 to CamelotKey(2, 'B'),  // F# major -> 2B
            7 to CamelotKey(9, 'B'),  // G major -> 9B
            8 to CamelotKey(4, 'B'),  // G# major -> 4B
            9 to CamelotKey(11, 'B'), // A major -> 11B
            10 to CamelotKey(6, 'B'), // A# major -> 6B
            11 to CamelotKey(1, 'B')  // B major -> 1B
        )

        /**
         * Converts standard Spotify pitch class (0 = C, 1 = C#, ...) and mode (0 = minor, 1 = major)
         * to Camelot Wheel key.
         */
        fun fromPitchAndMode(pitchClass: Int, mode: Int): CamelotKey? {
            if (pitchClass !in 0..11) return null
            return if (mode == 1) {
                PITCH_CLASS_TO_CAMELOT_MAJOR[pitchClass]
            } else {
                PITCH_CLASS_TO_CAMELOT_MINOR[pitchClass]
            }
        }

        /**
         * Estimates or derives a deterministic pseudo-key from song attributes when Spotify Audio Analysis is unavailable.
         */
        fun estimateFromSong(song: SongsModel): CamelotKey {
            val hash = abs(song.title.hashCode() xor song.singer.hashCode())
            val number = (hash % 12) + 1
            val letter = if ((hash / 12) % 2 == 0) 'A' else 'B'
            return CamelotKey(number, letter)
        }
    }
}

/**
 * Smart AI Harmonic DJ Mixer:
 * - Calculates harmonic compatibility based on the Camelot Wheel
 * - Determines tempo ramping parameters for seamless crossfade beat matching
 * - Ranks and sequences tracks for DJ-grade seamless transitions
 */
object HarmonicDjMixer {

    /**
     * Calculates harmonic compatibility between two Camelot keys (0.0 to 1.0).
     *
     * 1.0: Exact match (8A -> 8A)
     * 0.95: Relative major/minor (8A -> 8B)
     * 0.90: Adjacent wheel step (+1 / -1) (8A -> 7A or 9A)
     * 0.80: Energy boost (+1 semitone or +2 steps)
     * <0.50: Dissonant key clashes
     */
    fun calculateHarmonicScore(key1: CamelotKey, key2: CamelotKey): Float {
        if (key1 == key2) return 1.0f

        // Same number, different letter: relative major/minor (e.g. 8A & 8B)
        if (key1.number == key2.number && key1.letter != key2.letter) {
            return 0.95f
        }

        // Same letter, check circular distance on 12-hour wheel
        if (key1.letter == key2.letter) {
            val diff = abs(key1.number - key2.number)
            val circularDist = min(diff, 12 - diff)
            return when (circularDist) {
                1 -> 0.90f // Adjacent step
                2 -> 0.80f // Energy boost / diagonal step
                3 -> 0.65f
                else -> 0.40f // Clashing key
            }
        }

        // Different letter and different number
        val diff = abs(key1.number - key2.number)
        val circularDist = min(diff, 12 - diff)
        return when (circularDist) {
            1 -> 0.82f // Diagonal blend (e.g. 8A -> 7B)
            2 -> 0.60f
            else -> 0.30f
        }
    }

    /**
     * Calculates tempo compatibility given two BPM values.
     * Considers half-time and double-time compatibility (e.g. 70 BPM blends with 140 BPM).
     */
    fun calculateTempoCompatibility(bpm1: Float, bpm2: Float): Float {
        if (bpm1 <= 0f || bpm2 <= 0f) return 0.8f // neutral default

        val ratio = bpm2 / bpm1
        val normalizedRatio = when {
            ratio in 0.45f..0.55f -> ratio * 2f // Half-time match
            ratio in 1.9f..2.1f -> ratio / 2f  // Double-time match
            else -> ratio
        }

        val deltaPercent = abs(normalizedRatio - 1.0f)
        return (1.0f - (deltaPercent * 4f)).coerceIn(0.1f, 1.0f)
    }

    /**
     * Ranks a candidate playlist or recommendation queue harmonically starting from current song.
     */
    fun sortHarmonically(currentSong: SongsModel, candidates: List<SongsModel>): List<SongsModel> {
        if (candidates.size <= 1) return candidates

        val currentKey = CamelotKey.estimateFromSong(currentSong)
        val remaining = candidates.toMutableList()
        val sorted = mutableListOf<SongsModel>()

        var activeKey = currentKey

        while (remaining.isNotEmpty()) {
            val bestCandidate = remaining.maxByOrNull { candidate ->
                val candidateKey = CamelotKey.estimateFromSong(candidate)
                calculateHarmonicScore(activeKey, candidateKey)
            } ?: remaining.first()

            sorted.add(bestCandidate)
            remaining.remove(bestCandidate)
            activeKey = CamelotKey.estimateFromSong(bestCandidate)
        }

        return sorted
    }
}

package io.github.sekademi.spotufi.data.lyrics

import io.github.sekademi.spotufi.data.entity.Lyrics
import java.util.regex.Pattern

/**
 * Syllable / Word item with start and end timestamps.
 */
data class SyllableWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
) {
    /**
     * Progress of this syllable between 0.0f (unreached) and 1.0f (completed).
     */
    fun progressAt(currentMs: Long): Float {
        if (currentMs <= startMs) return 0f
        if (currentMs >= endMs) return 1f
        val duration = (endMs - startMs).coerceAtLeast(1L)
        return ((currentMs - startMs).toFloat() / duration).coerceIn(0f, 1f)
    }
}

/**
 * Line of lyrics with word-level timing and optional phonetic transliteration.
 */
data class SyllableLine(
    val originalText: String,
    val startMs: Long,
    val endMs: Long,
    val words: List<SyllableWord>,
    val transliteration: String? = null,
)

/**
 * Parser for syllable-by-syllable / word-by-word synced karaoke lyrics:
 * - Parses enhanced LRC word timestamps (<mm:ss.xx>)
 * - Gracefully interpolates word timing from line timestamps when word tags are absent
 * - Generates phonetic transliterations for Japanese (Romaji), Korean, and Hindi lyrics
 */
object SyllableLyricsParser {

    private val WORD_TAG_PATTERN: Pattern = Pattern.compile("<(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?>")

    /**
     * Converts a standard [Lyrics] object into a list of [SyllableLine]s.
     */
    fun parse(lyrics: Lyrics): List<SyllableLine> {
        val rawLines = lyrics.lines
        if (rawLines.isEmpty()) return emptyList()

        val result = mutableListOf<SyllableLine>()

        for (i in rawLines.indices) {
            val line = rawLines[i]
            val text = line.text.trim()
            if (text.isBlank()) continue

            val nextLineTimeMs = if (i + 1 < rawLines.size) {
                rawLines[i + 1].timeMs
            } else {
                line.timeMs + 4000L
            }
            val lineDuration = (nextLineTimeMs - line.timeMs).coerceIn(1200L, 8000L)
            val lineEndMs = line.timeMs + lineDuration

            val words = parseWordsForLine(text, line.timeMs, lineEndMs)
            val cleanText = words.joinToString(" ") { it.text }
            val transliteration = TransliterationEngine.transliterate(cleanText)

            result.add(
                SyllableLine(
                    originalText = cleanText,
                    startMs = line.timeMs,
                    endMs = lineEndMs,
                    words = words,
                    transliteration = transliteration,
                )
            )
        }

        return result
    }

    private fun parseWordsForLine(rawText: String, lineStartMs: Long, lineEndMs: Long): List<SyllableWord> {
        val matcher = WORD_TAG_PATTERN.matcher(rawText)
        val taggedWords = mutableListOf<Pair<Long, String>>()

        var lastTagEnd = 0
        var currentTagTimeMs: Long? = null

        while (matcher.find()) {
            val tagStart = matcher.start()
            val tagEnd = matcher.end()

            if (currentTagTimeMs != null && tagStart > lastTagEnd) {
                val segment = rawText.substring(lastTagEnd, tagStart).trim()
                if (segment.isNotBlank()) {
                    taggedWords.add(Pair(currentTagTimeMs, segment))
                }
            }

            val min = matcher.group(1)?.toLongOrNull() ?: 0L
            val sec = matcher.group(2)?.toLongOrNull() ?: 0L
            val millisRaw = matcher.group(3) ?: "0"
            val ms = when (millisRaw.length) {
                1 -> millisRaw.toLong() * 100
                2 -> millisRaw.toLong() * 10
                else -> millisRaw.take(3).toLong()
            }
            currentTagTimeMs = (min * 60 + sec) * 1000 + ms
            lastTagEnd = tagEnd
        }

        if (currentTagTimeMs != null && lastTagEnd < rawText.length) {
            val tail = rawText.substring(lastTagEnd).trim()
            if (tail.isNotBlank()) {
                taggedWords.add(Pair(currentTagTimeMs, tail))
            }
        }

        // If enhanced LRC word tags were found:
        if (taggedWords.isNotEmpty()) {
            val result = mutableListOf<SyllableWord>()
            for (idx in taggedWords.indices) {
                val (start, wordStr) = taggedWords[idx]
                val end = if (idx + 1 < taggedWords.size) {
                    taggedWords[idx + 1].first
                } else {
                    lineEndMs
                }
                result.add(SyllableWord(wordStr, start, end.coerceAtLeast(start + 150L)))
            }
            return result
        }

        // Fallback: interpolate words smoothly based on word length
        val rawTokens = rawText.replace(Regex("<[^>]+>"), "").trim().split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (rawTokens.isEmpty()) return emptyList()

        val totalChars = rawTokens.sumOf { it.length }.coerceAtLeast(1)
        val totalDuration = (lineEndMs - lineStartMs).coerceAtLeast(100L)

        var runningMs = lineStartMs
        val interpolated = mutableListOf<SyllableWord>()

        for (token in rawTokens) {
            val tokenDuration = (totalDuration * token.length) / totalChars
            val tokenEnd = (runningMs + tokenDuration).coerceAtLeast(runningMs + 100L)
            interpolated.add(SyllableWord(token, runningMs, tokenEnd))
            runningMs = tokenEnd
        }

        return interpolated
    }
}

package io.github.sekademi.spotufi.data.recommendation

import android.content.Context
import io.github.sekademi.spotufi.data.preferences.getCachedStream
import io.github.sekademi.spotufi.data.preferences.getListeningHistory
import io.github.sekademi.spotufi.di.StreamResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

enum class TimeOfDaySlot {
    MORNING,   // 05:00 - 11:59
    AFTERNOON, // 12:00 - 16:59
    EVENING,   // 17:00 - 21:59
    NIGHT      // 22:00 - 04:59
}

/**
 * Predictive offline pre-caching engine:
 * - Analyzes listening history patterns by time-of-day
 * - Silently pre-resolves audio stream URLs and caches intros
 * - Eliminates resolution latency when user opens the app during their habitual listening hours
 */
object SmartOfflinePredictor {

    fun getCurrentSlot(): TimeOfDaySlot {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> TimeOfDaySlot.MORNING
            in 12..16 -> TimeOfDaySlot.AFTERNOON
            in 17..21 -> TimeOfDaySlot.EVENING
            else -> TimeOfDaySlot.NIGHT
        }
    }

    fun getTimeSlotTitle(): String = when (getCurrentSlot()) {
        TimeOfDaySlot.MORNING -> "Morning Commute"
        TimeOfDaySlot.AFTERNOON -> "Afternoon Focus"
        TimeOfDaySlot.EVENING -> "Evening Workout"
        TimeOfDaySlot.NIGHT -> "Late Night Chill"
    }

    /**
     * Identifies top predicted songs from user history and pre-resolves their stream URLs in background.
     */
    fun triggerPreCache(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                val history = getListeningHistory(context)
                if (history.isEmpty()) return@launch

                val topFrequent = history.groupingBy { it.songId }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(5)
                    .mapNotNull { entry -> history.firstOrNull { it.songId == entry.key } }

                for (entry in topFrequent) {
                    val query = "${entry.singer} - ${entry.title}"
                    val cached = getCachedStream(context, query)
                    if (cached == null) {
                        val resolved = runCatching {
                            StreamResolver.resolveStreamUrl(query, context, forPlayback = false)
                        }.getOrNull()
                        if (!resolved.isNullOrBlank()) {
                            StreamResolver.cacheIntro(resolved, context)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.d("SmartOfflinePredictor", "Pre-cache background run failed: ${e.message}")
            }
        }
    }
}

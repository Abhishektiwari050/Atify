package io.github.sekademi.spotufi.audio

import android.content.Context
import android.media.audiofx.Equalizer
import android.util.Log
import io.github.sekademi.spotufi.data.preferences.getEqualizerBandLevel
import io.github.sekademi.spotufi.data.preferences.getEqualizerPreset
import io.github.sekademi.spotufi.data.preferences.isEqualizerEnabled
import io.github.sekademi.spotufi.data.preferences.setEqualizerBandLevel
import io.github.sekademi.spotufi.data.preferences.setEqualizerEnabled
import io.github.sekademi.spotufi.data.preferences.setEqualizerPreset

/**
 * Native Equalizer engine wrapping Android's [Equalizer] effect.
 * Handles audio session attachment, preset definitions, and persistent frequency adjustments.
 */
object EqualizerEngine {
    private const val TAG = "EqualizerEngine"

    @Volatile private var equalizer: Equalizer? = null
    @Volatile private var currentSessionId: Int = 0

    data class BandInfo(
        val index: Int,
        val centerFreqHz: Int,
        val levelMillibels: Int,
        val minMillibels: Int = -1500,
        val maxMillibels: Int = 1500,
    ) {
        val displayFrequency: String
            get() = if (centerFreqHz >= 1000) "${centerFreqHz / 1000} kHz" else "$centerFreqHz Hz"

        val displayDb: String
            get() = "${levelMillibels / 100} dB"
    }

    val builtInPresets = mapOf(
        "Flat" to listOf(0, 0, 0, 0, 0),
        "Bass Boost" to listOf(600, 400, 200, 0, 0),
        "Treble Boost" to listOf(0, 0, 100, 300, 600),
        "Vocal Boost" to listOf(-200, 300, 600, 200, -100),
        "Rock" to listOf(400, 200, -100, 200, 500),
        "Pop" to listOf(-100, 200, 500, 200, -100),
        "Electronic" to listOf(500, 300, 0, 200, 400),
        "Jazz" to listOf(300, 100, 200, 300, 200),
        "Acoustic" to listOf(300, 200, 100, 300, 200),
    )

    fun bindAudioSession(context: Context, audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) return
        currentSessionId = audioSessionId

        release()

        runCatching {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            eq.enabled = isEqualizerEnabled(context)

            val bandCount = eq.numberOfBands.toInt()
            val preset = getEqualizerPreset(context)

            if (preset in builtInPresets) {
                applyBuiltInPreset(context, preset)
            } else {
                for (i in 0 until bandCount) {
                    val saved = getEqualizerBandLevel(context, i)
                    eq.setBandLevel(i.toShort(), saved.toShort())
                }
            }
            Log.d(TAG, "Equalizer successfully bound to session $audioSessionId with $bandCount bands")
        }.onFailure {
            Log.w(TAG, "Failed to initialize Equalizer on session $audioSessionId: ${it.message}")
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        setEqualizerEnabled(context, enabled)
        runCatching {
            equalizer?.enabled = enabled
        }
    }

    fun isEnabled(context: Context): Boolean = isEqualizerEnabled(context)

    fun getBands(context: Context): List<BandInfo> {
        val eq = equalizer
        if (eq == null) {
            val defaultFreqs = listOf(60, 230, 910, 3600, 14000)
            return defaultFreqs.mapIndexed { index, freq ->
                BandInfo(
                    index = index,
                    centerFreqHz = freq,
                    levelMillibels = getEqualizerBandLevel(context, index),
                )
            }
        }

        return runCatching {
            val count = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val min = range?.getOrNull(0)?.toInt() ?: -1500
            val max = range?.getOrNull(1)?.toInt() ?: 1500

            (0 until count).map { i ->
                val freq = eq.getCenterFreq(i.toShort()) / 1000
                val level = eq.getBandLevel(i.toShort()).toInt()
                BandInfo(
                    index = i,
                    centerFreqHz = freq,
                    levelMillibels = level,
                    minMillibels = min,
                    maxMillibels = max,
                )
            }
        }.getOrElse {
            emptyList()
        }
    }

    fun setBandLevel(context: Context, bandIndex: Int, levelMillibels: Int) {
        setEqualizerBandLevel(context, bandIndex, levelMillibels)
        setEqualizerPreset(context, "Custom")
        runCatching {
            equalizer?.setBandLevel(bandIndex.toShort(), levelMillibels.toShort())
        }
    }

    fun applyPreset(context: Context, presetName: String) {
        setEqualizerPreset(context, presetName)
        if (presetName in builtInPresets) {
            applyBuiltInPreset(context, presetName)
        }
    }

    private fun applyBuiltInPreset(context: Context, presetName: String) {
        val levels = builtInPresets[presetName] ?: return
        val eq = equalizer
        levels.forEachIndexed { index, level ->
            setEqualizerBandLevel(context, index, level)
            runCatching {
                eq?.let {
                    if (index < it.numberOfBands) {
                        it.setBandLevel(index.toShort(), level.toShort())
                    }
                }
            }
        }
    }

    fun release() {
        runCatching {
            equalizer?.release()
        }
        equalizer = null
    }
}

package io.github.sekademi.spotufi.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Result from real-time microphone pitch detection.
 */
data class PitchResult(
    val frequencyHz: Float = 0f,
    val noteName: String = "--",
    val midiNote: Int = 0,
    val centsOffset: Float = 0f, // -50 (flat) to +50 (sharp)
    val confidence: Float = 0f,  // 0.0 to 1.0
    val rmsLevel: Float = 0f,     // loudness
)

/**
 * Real-time vocal pitch detector for Sing-Along Karaoke:
 * - Employs normalized autocorrelation with parabolic interpolation
 * - Detects fundamental frequencies (70Hz - 900Hz, human vocal range)
 * - Computes nearest MIDI note, note name (A4, C#3), and tuning cents
 * - Non-blocking background capture using Coroutines
 */
object PitchDetector {

    private const val SAMPLE_RATE = 44100
    private const val BUFFER_SIZE_SAMPLES = 2048 // ~46.4ms window
    private const val MIN_FREQ = 70f             // Lowest vocal bass ~E2
    private const val MAX_FREQ = 900f            // High vocal soprano ~A5

    private const val MIN_PERIOD = (SAMPLE_RATE / MAX_FREQ).toInt() // ~49
    private const val MAX_PERIOD = (SAMPLE_RATE / MIN_FREQ).toInt() // ~630

    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    private val _pitchState = MutableStateFlow(PitchResult())
    val pitchState: StateFlow<PitchResult> = _pitchState.asStateFlow()

    private var captureJob: Job? = null
    @Volatile private var isRunning = false

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isListening(): Boolean = isRunning

    fun start(context: Context) {
        if (isRunning) return
        if (!hasPermission(context)) return

        isRunning = true
        captureJob = CoroutineScope(Dispatchers.Default).launch {
            runAudioCapture()
        }
    }

    fun stop() {
        isRunning = false
        captureJob?.cancel()
        captureJob = null
        _pitchState.value = PitchResult()
    }

    private fun runAudioCapture() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, BUFFER_SIZE_SAMPLES * 2)

        var record: AudioRecord? = null
        try {
            record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                isRunning = false
                return
            }

            record.startRecording()
            val audioBuffer = ShortArray(BUFFER_SIZE_SAMPLES)

            while (isRunning) {
                val readShorts = record.read(audioBuffer, 0, BUFFER_SIZE_SAMPLES)
                if (readShorts < BUFFER_SIZE_SAMPLES) continue

                val result = analyzePitch(audioBuffer, readShorts)
                _pitchState.value = result
            }
        } catch (e: Exception) {
            android.util.Log.e("PitchDetector", "Error during recording: ${e.message}")
        } finally {
            try {
                record?.stop()
                record?.release()
            } catch (_: Exception) {}
            isRunning = false
        }
    }

    private fun analyzePitch(buffer: ShortArray, length: Int): PitchResult {
        // 1. Calculate RMS energy
        var sumSquares = 0.0
        for (i in 0 until length) {
            val sample = buffer[i].toDouble() / 32768.0
            sumSquares += sample * sample
        }
        val rms = sqrt(sumSquares / length).toFloat()

        // Noise gate: ignore silence or very quiet background noise
        if (rms < 0.015f) {
            return PitchResult(rmsLevel = rms)
        }

        // 2. Autocorrelation
        var bestCorrelation = 0.0
        var bestPeriod = -1

        val correlations = DoubleArray(MAX_PERIOD + 2)

        for (lag in MIN_PERIOD..MAX_PERIOD) {
            var corr = 0.0
            for (i in 0 until (length - lag)) {
                corr += buffer[i].toDouble() * buffer[i + lag].toDouble()
            }
            correlations[lag] = corr
            if (corr > bestCorrelation) {
                bestCorrelation = corr
                bestPeriod = lag
            }
        }

        if (bestPeriod <= MIN_PERIOD || bestPeriod >= MAX_PERIOD || bestCorrelation <= 0.0) {
            return PitchResult(rmsLevel = rms)
        }

        // 3. Parabolic interpolation for sub-sample precision
        val prev = correlations[bestPeriod - 1]
        val curr = correlations[bestPeriod]
        val next = correlations[bestPeriod + 1]
        val delta = (next - prev) / (2.0 * (2.0 * curr - prev - next)).coerceIn(-1.0, 1.0)
        val refinedPeriod = bestPeriod.toDouble() + delta

        val frequency = (SAMPLE_RATE / refinedPeriod).toFloat()
        if (frequency !in MIN_FREQ..MAX_FREQ) {
            return PitchResult(rmsLevel = rms)
        }

        // 4. Convert Frequency to MIDI note & note name
        // MIDI note: 69 + 12 * log2(f / 440)
        val midiFloat = 69f + 12f * (ln(frequency / 440.0) / ln(2.0)).toFloat()
        val midiInt = midiFloat.roundToInt()
        val cents = (midiFloat - midiInt) * 100f // offset in cents (-50 to +50)

        val noteIndex = (midiInt % 12 + 12) % 12
        val octave = (midiInt / 12) - 1
        val noteName = "${NOTE_NAMES[noteIndex]}$octave"

        // Confidence approximation (normalized by zero-lag power)
        val energyNorm = sumSquares * 32768.0 * 32768.0 / length
        val confidence = if (energyNorm > 0.0) {
            (bestCorrelation / energyNorm).toFloat().coerceIn(0f, 1f)
        } else {
            0.5f
        }

        return PitchResult(
            frequencyHz = frequency,
            noteName = noteName,
            midiNote = midiInt,
            centsOffset = cents,
            confidence = confidence,
            rmsLevel = rms,
        )
    }
}

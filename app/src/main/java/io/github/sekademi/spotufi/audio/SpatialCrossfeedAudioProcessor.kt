package io.github.sekademi.spotufi.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Studio-Grade Binaural Spatial Crossfeed Audio Processor.
 *
 * Simulates the natural acoustic head-shadow and interaural time difference (ITD)
 * of listening to stereo speakers in a treated listening room:
 * 1. Feeds cross-channel signal (Left -> Right, Right -> Left) through a head-shadow
 *    low-pass filter (cutoff 700 Hz) with ~30% (-10.5 dB) level.
 * 2. Implements a 300-microsecond interaural delay (13-15 samples) matching the
 *    acoustic distance between human ears.
 *
 * Eliminates "in-head" localization and listening fatigue during long headphone sessions.
 */
@UnstableApi
class SpatialCrossfeedAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var isEnabled: Boolean = false

    private var sampleRate: Int = 0
    private var channelCount: Int = 0
    private var encoding: Int = C.ENCODING_PCM_16BIT

    // Head-shadow 1st-order IIR LPF filter coefficients at ~700 Hz
    private var lpfAlpha: Double = 0.1
    private var crossfeedGain: Double = 0.30 // -10.5 dB

    // Filter states
    private var filterStateL: Double = 0.0
    private var filterStateR: Double = 0.0

    // Delay ring buffers (capacity for up to 96kHz 300us = 32 samples)
    private val delayBufferSize = 64
    private val delayBufferL = DoubleArray(delayBufferSize)
    private val delayBufferR = DoubleArray(delayBufferSize)
    private var delayWriteIndex = 0
    private var delaySamples = 14

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }

        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        // Interaural delay: ~300 microseconds
        val delaySec = 0.000300
        delaySamples = (sampleRate * delaySec).toInt().coerceIn(1, delayBufferSize - 1)

        // 700 Hz LPF cutoff calculation
        val fc = 700.0
        val dt = 1.0 / sampleRate
        val rc = 1.0 / (2.0 * PI * fc)
        lpfAlpha = dt / (rc + dt)

        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!isEnabled || channelCount != 2) {
            val outputBuffer = replaceOutputBuffer(remaining)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val outputBuffer = replaceOutputBuffer(remaining)

        if (encoding == C.ENCODING_PCM_16BIT) {
            processPcm16(inputBuffer, outputBuffer)
        } else if (encoding == C.ENCODING_PCM_FLOAT) {
            processPcmFloat(inputBuffer, outputBuffer)
        } else {
            outputBuffer.put(inputBuffer)
        }

        outputBuffer.flip()
    }

    private fun processPcm16(input: ByteBuffer, output: ByteBuffer) {
        val shortCount = input.remaining() / 2
        val frames = shortCount / 2

        val readIndex = (delayWriteIndex - delaySamples + delayBufferSize) % delayBufferSize

        for (i in 0 until frames) {
            val rawL = input.short.toDouble() / 32768.0
            val rawR = input.short.toDouble() / 32768.0

            // 1. Low-pass filter opposing signals
            filterStateL += lpfAlpha * (rawL - filterStateL)
            filterStateR += lpfAlpha * (rawR - filterStateR)

            // 2. Put filtered signal into delay ring buffer
            val currentWrite = (delayWriteIndex + i) % delayBufferSize
            delayBufferL[currentWrite] = filterStateL * crossfeedGain
            delayBufferR[currentWrite] = filterStateR * crossfeedGain

            // 3. Read delayed cross-signal
            val currentRead = (readIndex + i) % delayBufferSize
            val crossToR = delayBufferL[currentRead]
            val crossToL = delayBufferR[currentRead]

            // 4. Mix direct + cross-fed delayed opposing channel
            val outL = (rawL + crossToL).coerceIn(-1.0, 1.0)
            val outR = (rawR + crossToR).coerceIn(-1.0, 1.0)

            output.putShort((outL * 32767.0).toInt().toShort())
            output.putShort((outR * 32767.0).toInt().toShort())
        }

        delayWriteIndex = (delayWriteIndex + frames) % delayBufferSize
    }

    private fun processPcmFloat(input: ByteBuffer, output: ByteBuffer) {
        val floatCount = input.remaining() / 4
        val frames = floatCount / 2
        val readIndex = (delayWriteIndex - delaySamples + delayBufferSize) % delayBufferSize

        for (i in 0 until frames) {
            val rawL = input.float.toDouble()
            val rawR = input.float.toDouble()

            filterStateL += lpfAlpha * (rawL - filterStateL)
            filterStateR += lpfAlpha * (rawR - filterStateR)

            val currentWrite = (delayWriteIndex + i) % delayBufferSize
            delayBufferL[currentWrite] = filterStateL * crossfeedGain
            delayBufferR[currentWrite] = filterStateR * crossfeedGain

            val currentRead = (readIndex + i) % delayBufferSize
            val crossToR = delayBufferL[currentRead]
            val crossToL = delayBufferR[currentRead]

            val outL = (rawL + crossToL).coerceIn(-1.0, 1.0)
            val outR = (rawR + crossToR).coerceIn(-1.0, 1.0)

            output.putFloat(outL.toFloat())
            output.putFloat(outR.toFloat())
        }

        delayWriteIndex = (delayWriteIndex + frames) % delayBufferSize
    }

    override fun onReset() {
        filterStateL = 0.0
        filterStateR = 0.0
        delayBufferL.fill(0.0)
        delayBufferR.fill(0.0)
        delayWriteIndex = 0
    }
}

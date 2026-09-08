package io.github.sekademi.spotufi.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Media3 [AudioProcessor] applying a real-time Biquad low-pass / high-pass filter to the
 * audio stream, for DJ-style crossfade transitions. Ported from SimpMusic.
 *
 * - [enabled], [cutoffFrequencyHz] and [filterType] are runtime-mutable (thread-safe).
 * - When [enabled] is false the audio passes through unmodified (near-zero overhead).
 * - Coefficient recalculation is lazy: only when cutoff or filter type changes.
 * - Supports PCM 16-bit and PCM 32-bit float (mono and stereo).
 */
@UnstableApi
class CrossfadeFilterAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (!value) filter.reset()
            }
        }

    @Volatile
    var cutoffFrequencyHz: Float = 20000f
        set(value) {
            if (field != value) {
                field = value
                coefficientsDirty = true
            }
        }

    @Volatile
    var filterType: BiquadFilter.FilterType = BiquadFilter.FilterType.LOW_PASS
        set(value) {
            if (field != value) {
                field = value
                coefficientsDirty = true
            }
        }

    private val filter = BiquadFilter()

    @Volatile
    private var coefficientsDirty = true

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_PCM_16BIT

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        coefficientsDirty = true
        return inputAudioFormat
    }

    // NOTE: do NOT override isActive() to true — onConfigure returns NOT_SET for
    // non-16-bit input (e.g. 24-bit hi-res FLAC), and claiming to be active with an
    // unset format broke the audio pipeline: lossless downloads played silently.
    // BaseAudioProcessor's isActive() correctly deactivates us so such streams
    // bypass the filter and reach the sink untouched.

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || sampleRate == 0) {
            val output = replaceOutputBuffer(remaining)
            copyBuffer(inputBuffer, output, remaining)
            output.flip()
            return
        }

        if (coefficientsDirty) {
            filter.updateCoefficients(
                cutoffHz = cutoffFrequencyHz,
                sampleRate = sampleRate,
                type = filterType,
            )
            coefficientsDirty = false
        }

        val output = replaceOutputBuffer(remaining)
        inputBuffer.order(ByteOrder.nativeOrder())
        output.order(ByteOrder.nativeOrder())

        when (encoding) {
            C.ENCODING_PCM_16BIT -> when (channelCount) {
                1 -> processMono16(inputBuffer, output)
                2 -> processStereo16(inputBuffer, output)
                else -> copyBuffer(inputBuffer, output, remaining)
            }
            C.ENCODING_PCM_FLOAT -> when (channelCount) {
                1 -> processMonoFloat(inputBuffer, output)
                2 -> processStereoFloat(inputBuffer, output)
                else -> copyBuffer(inputBuffer, output, remaining)
            }
            else -> copyBuffer(inputBuffer, output, remaining)
        }

        output.flip()
    }

    private fun copyBuffer(src: ByteBuffer, dst: ByteBuffer, size: Int) {
        if (src === dst) {
            dst.position(0)
            dst.limit(size)
            return
        }
        val pos = src.position()
        for (i in 0 until size) {
            dst.put(src.get(pos + i))
        }
        src.position(pos + size)
    }

    private fun processMono16(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 2) {
            val sample = input.short.toDouble() / Short.MAX_VALUE
            val filtered = filter.processSampleMono(sample)
            output.putShort((filtered.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort())
        }
    }

    private fun processStereo16(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 4) {
            val left = input.short.toDouble() / Short.MAX_VALUE
            val right = input.short.toDouble() / Short.MAX_VALUE
            val out = filter.processStereo(left, right)
            output.putShort((out[0].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort())
            output.putShort((out[1].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort())
        }
    }

    private fun processMonoFloat(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 4) {
            val sample = input.float.toDouble()
            val filtered = filter.processSampleMono(sample)
            output.putFloat(filtered.coerceIn(-1.0, 1.0).toFloat())
        }
    }

    private fun processStereoFloat(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 8) {
            val left = input.float.toDouble()
            val right = input.float.toDouble()
            val out = filter.processStereo(left, right)
            output.putFloat(out[0].coerceIn(-1.0, 1.0).toFloat())
            output.putFloat(out[1].coerceIn(-1.0, 1.0).toFloat())
        }
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        super.onFlush(streamMetadata)
        filter.reset()
    }

    override fun onReset() {
        super.onReset()
        enabled = false
        cutoffFrequencyHz = 20000f
        filterType = BiquadFilter.FilterType.LOW_PASS
        filter.reset()
    }
}

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
 * High-performance, zero-allocation real-time Vocal Remover and Karaoke processor.
 *
 * Employs center-channel vocal band isolation with 3-band crossover filtering:
 * 1. Sub-bass (<180 Hz) is completely preserved without phase cancellation or bass thinning.
 * 2. High frequency air (>5.5 kHz) is preserved for crisp cymbals and spatial presence.
 * 3. Vocal formant band (180 Hz - 5.5 kHz) center channel (Mid) is selectively attenuated
 *    by [attenuation] (0.0 = full vocals, 1.0 = complete vocal cancellation).
 *
 * Runs directly on PCM audio buffers in ExoPlayer's AudioSink with zero heap allocations.
 */
@UnstableApi
class VocalRemoverAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var attenuation: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    val isKaraokeActive: Boolean
        get() = attenuation > 0.005f

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_PCM_16BIT

    // ── 2nd-order Butterworth crossover coefficients on Center (Mid) Channel ──
    // LPF at 200 Hz for sub-bass preservation (kick drum, bassline)
    private var lp_b0 = 1.0; private var lp_b1 = 0.0; private var lp_b2 = 0.0
    private var lp_a1 = 0.0; private var lp_a2 = 0.0

    // HPF at 6000 Hz for high-frequency air/sparkle preservation (cymbals, presence)
    private var hp_b0 = 1.0; private var hp_b1 = 0.0; private var hp_b2 = 0.0
    private var hp_a1 = 0.0; private var hp_a2 = 0.0

    // Direct Form I state for LPF (Bass)
    private var lp_x1 = 0.0; private var lp_x2 = 0.0
    private var lp_y1 = 0.0; private var lp_y2 = 0.0

    // Direct Form I state for HPF (Treble)
    private var hp_x1 = 0.0; private var hp_x2 = 0.0
    private var hp_y1 = 0.0; private var hp_y2 = 0.0

    private var coefficientsDirty = true

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

    private fun updateFilterCoefficients(sr: Int) {
        if (sr <= 0) return
        val q = 0.7071067811865476 // Butterworth Q

        // 1. Sub-bass LPF at 200 Hz
        val lpCutoff = 200.0.coerceAtMost(sr / 2.0 - 1.0)
        val lpOmega = 2.0 * PI * lpCutoff / sr
        val lpSin = sin(lpOmega)
        val lpCos = cos(lpOmega)
        val lpAlpha = lpSin / (2.0 * q)
        val lpA0 = 1.0 + lpAlpha

        lp_b0 = ((1.0 - lpCos) / 2.0) / lpA0
        lp_b1 = (1.0 - lpCos) / lpA0
        lp_b2 = ((1.0 - lpCos) / 2.0) / lpA0
        lp_a1 = (-2.0 * lpCos) / lpA0
        lp_a2 = (1.0 - lpAlpha) / lpA0

        // 2. High-treble HPF at 6000 Hz
        val hpCutoff = 6000.0.coerceAtMost(sr / 2.0 - 1.0)
        val hpOmega = 2.0 * PI * hpCutoff / sr
        val hpSin = sin(hpOmega)
        val hpCos = cos(hpOmega)
        val hpAlpha = hpSin / (2.0 * q)
        val hpA0 = 1.0 + hpAlpha

        hp_b0 = ((1.0 + hpCos) / 2.0) / hpA0
        hp_b1 = (-(1.0 + hpCos)) / hpA0
        hp_b2 = ((1.0 + hpCos) / 2.0) / hpA0
        hp_a1 = (-2.0 * hpCos) / hpA0
        hp_a2 = (1.0 - hpAlpha) / hpA0

        coefficientsDirty = false
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // If karaoke is inactive or mono, pass through unmodified with maximum throughput
        if (!isKaraokeActive || channelCount != 2 || sampleRate == 0) {
            val output = replaceOutputBuffer(remaining)
            copyBuffer(inputBuffer, output, remaining)
            output.flip()
            return
        }

        if (coefficientsDirty) {
            updateFilterCoefficients(sampleRate)
        }

        val output = replaceOutputBuffer(remaining)
        inputBuffer.order(ByteOrder.nativeOrder())
        output.order(ByteOrder.nativeOrder())

        val alpha = attenuation.toDouble()

        when (encoding) {
            C.ENCODING_PCM_16BIT -> processStereo16(inputBuffer, output, alpha)
            C.ENCODING_PCM_FLOAT -> processStereoFloat(inputBuffer, output, alpha)
            else -> copyBuffer(inputBuffer, output, remaining)
        }

        output.flip()
    }

    private fun processStereo16(input: ByteBuffer, output: ByteBuffer, alpha: Double) {
        val maxVal = Short.MAX_VALUE.toDouble()
        val vocalGain = (1.0 - alpha).coerceIn(0.0, 1.0)

        while (input.remaining() >= 4) {
            val left = input.short.toDouble() / maxVal
            val right = input.short.toDouble() / maxVal

            // Orthogonal Mid/Side decomposition
            val mid = (left + right) * 0.5
            val side = (left - right) * 0.5

            // Sub-bass preservation via LPF
            val midBass = lp_b0 * mid + lp_b1 * lp_x1 + lp_b2 * lp_x2 - lp_a1 * lp_y1 - lp_a2 * lp_y2
            lp_x2 = lp_x1; lp_x1 = mid
            lp_y2 = lp_y1; lp_y1 = midBass

            // Treble presence preservation via HPF
            val midTreble = hp_b0 * mid + hp_b1 * hp_x1 + hp_b2 * hp_x2 - hp_a1 * hp_y1 - hp_a2 * hp_y2
            hp_x2 = hp_x1; hp_x1 = mid
            hp_y2 = hp_y1; hp_y1 = midTreble

            // Center vocal formant band is the remaining mid spectrum
            val midVocal = mid - midBass - midTreble

            // Attenuate only the vocal formant band, preserving sub-bass and high air
            val midOut = midBass + midTreble + vocalGain * midVocal

            // Recombine with untouched stereo side channel
            val outL = (midOut + side).coerceIn(-1.0, 1.0)
            val outR = (midOut - side).coerceIn(-1.0, 1.0)

            output.putShort((outL * maxVal).toInt().toShort())
            output.putShort((outR * maxVal).toInt().toShort())
        }
    }

    private fun processStereoFloat(input: ByteBuffer, output: ByteBuffer, alpha: Double) {
        val vocalGain = (1.0 - alpha).coerceIn(0.0, 1.0)

        while (input.remaining() >= 8) {
            val left = input.float.toDouble()
            val right = input.float.toDouble()

            // Orthogonal Mid/Side decomposition
            val mid = (left + right) * 0.5
            val side = (left - right) * 0.5

            // Sub-bass preservation via LPF
            val midBass = lp_b0 * mid + lp_b1 * lp_x1 + lp_b2 * lp_x2 - lp_a1 * lp_y1 - lp_a2 * lp_y2
            lp_x2 = lp_x1; lp_x1 = mid
            lp_y2 = lp_y1; lp_y1 = midBass

            // Treble presence preservation via HPF
            val midTreble = hp_b0 * mid + hp_b1 * hp_x1 + hp_b2 * hp_x2 - hp_a1 * hp_y1 - hp_a2 * hp_y2
            hp_x2 = hp_x1; hp_x1 = mid
            hp_y2 = hp_y1; hp_y1 = midTreble

            // Center vocal formant band is the remaining mid spectrum
            val midVocal = mid - midBass - midTreble

            // Attenuate only the vocal formant band, preserving sub-bass and high air
            val midOut = midBass + midTreble + vocalGain * midVocal

            val outL = (midOut + side).coerceIn(-1.0, 1.0).toFloat()
            val outR = (midOut - side).coerceIn(-1.0, 1.0).toFloat()

            output.putFloat(outL)
            output.putFloat(outR)
        }
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

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        super.onFlush(streamMetadata)
        resetFilterState()
    }

    override fun onReset() {
        super.onReset()
        // Do NOT reset attenuation to 0f — keep user's active vocal reduction preference
        resetFilterState()
    }

    private fun resetFilterState() {
        lp_x1 = 0.0; lp_x2 = 0.0; lp_y1 = 0.0; lp_y2 = 0.0
        hp_x1 = 0.0; hp_x2 = 0.0; hp_y1 = 0.0; hp_y2 = 0.0
    }
}

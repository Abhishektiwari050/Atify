package io.github.sekademi.spotufi.data.entity

/**
 * Detailed live technical metadata for the currently active audio stream.
 */
data class AudioStreamDetails(
    val source: String = "YouTube",
    val quality: String = "",
    val format: String = "",
    val sampleRateHz: Int = 0,
    val channelCount: Int = 0,
    val bitrateBps: Int = 0,
    val isOffloadActive: Boolean = false,
) {
    val formattedBitrate: String
        get() = when {
            bitrateBps > 0 -> "${bitrateBps / 1000} kbps"
            quality.contains("FLAC", ignoreCase = true) || quality.contains("Hi-Res", ignoreCase = true) -> "Lossless (~900+ kbps)"
            quality.contains("HIGH", ignoreCase = true) -> "~160-256 kbps"
            quality.isNotBlank() -> quality
            else -> "Standard (~128-160 kbps)"
        }

    val formattedSampleRate: String
        get() = if (sampleRateHz > 0) "${sampleRateHz / 1000.0} kHz" else "44.1 kHz"

    val formattedChannels: String
        get() = when (channelCount) {
            1 -> "Mono (1.0)"
            2 -> "Stereo (2.0)"
            6 -> "Surround (5.1)"
            else -> if (channelCount > 0) "$channelCount Channels" else "Stereo (2.0)"
        }

    val displayCodec: String
        get() = when {
            format.isNotBlank() -> format
            quality.contains("FLAC", ignoreCase = true) -> "FLAC"
            source == "YouTube" -> "Opus / AAC"
            else -> "Audio Stream"
        }
}

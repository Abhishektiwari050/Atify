package com.metrolist.spotify.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyAudioFeatures(
    val id: String = "",
    val key: Int = -1,
    val mode: Int = -1,
    val tempo: Float = 0f,
    val danceability: Float = 0f,
    val energy: Float = 0f,
    val valence: Float = 0f,
    @SerialName("duration_ms") val durationMs: Int = 0,
)

@Serializable
data class SpotifyAudioFeaturesResponse(
    @SerialName("audio_features") val audioFeatures: List<SpotifyAudioFeatures?> = emptyList(),
)

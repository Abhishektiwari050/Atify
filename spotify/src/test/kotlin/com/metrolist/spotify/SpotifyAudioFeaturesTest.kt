package com.metrolist.spotify

import com.metrolist.spotify.models.SpotifyAudioFeatures
import com.metrolist.spotify.models.SpotifyAudioFeaturesResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SpotifyAudioFeaturesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testAudioFeaturesDeserialization() {
        val payload = """
            {
              "audio_features": [
                {
                  "id": "4JpUtgH5Tj7mHj8q99z",
                  "key": 0,
                  "mode": 1,
                  "tempo": 128.0,
                  "danceability": 0.85,
                  "energy": 0.92,
                  "valence": 0.65,
                  "duration_ms": 210000
                },
                null
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<SpotifyAudioFeaturesResponse>(payload)
        val features = response.audioFeatures.filterNotNull()

        assertEquals(1, features.size)
        val feat = features[0]
        assertEquals("4JpUtgH5Tj7mHj8q99z", feat.id)
        assertEquals(0, feat.key)
        assertEquals(1, feat.mode)
        assertEquals(128.0f, feat.tempo, 0.01f)
        assertEquals(0.92f, feat.energy, 0.01f)
    }
}

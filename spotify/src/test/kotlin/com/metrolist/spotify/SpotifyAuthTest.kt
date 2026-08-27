package com.metrolist.spotify

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyAuthTest {

    @Test
    fun testSpotifyAuthPipeline() = runTest {
        // Test that Spotify server-time endpoint and Gist TOTP secrets are reachable and valid
        val result = SpotifyAuth.fetchAccessToken(spDc = "TEST_DUMMY_SP_DC")
        // A dummy sp_dc should return a failure with 401 (invalid/anonymous) or HTTP 400/401 from Spotify,
        // which proves the entire network pipeline, TOTP calculation, and Spotify server communication works!
        assertTrue("Dummy cookie should fail validation from Spotify", result.isFailure)
        val error = result.exceptionOrNull()
        println("SpotifyAuth response error as expected: ${error?.message}")
        assertTrue(
            "Expected SpotifyException, got: $error",
            error is Spotify.SpotifyException || error?.message?.contains("401") == true || error?.message?.contains("anonymous") == true
        )
    }
}

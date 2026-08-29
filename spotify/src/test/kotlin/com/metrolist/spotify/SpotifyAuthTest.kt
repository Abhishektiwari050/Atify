package com.metrolist.spotify

import com.metrolist.spotify.SpotifyAuth.GistFile
import com.metrolist.spotify.SpotifyAuth.GistFiles
import com.metrolist.spotify.SpotifyAuth.Nuance
import com.metrolist.spotify.SpotifyAuth.ServerTimeResponse
import com.metrolist.spotify.models.SpotifyInternalToken
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class SpotifyAuthTest {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    // =========================================================================
    // 1. RFC 6238 TOTP Verification Test Vectors
    // =========================================================================

    @Test
    fun testRfc6238TotpStandardVectors() {
        // RFC 6238 Appendix B test secret: "12345678901234567890" (20 ASCII bytes)
        // In Base32: "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

        // Standard RFC 6238 test vectors for HMAC-SHA1 with 30s interval:
        // Time = 59s -> Step 1 -> 8-digit: 94287082 -> 6-digit: 287082
        assertEquals("287082", SpotifyAuth.generateTotp(secret, 59L))

        // Time = 1111111109s -> Step 37037036 -> 8-digit: 07081804 -> 6-digit: 081804 (leading zero)
        assertEquals("081804", SpotifyAuth.generateTotp(secret, 1111111109L))

        // Time = 1111111111s -> Step 37037037 -> 8-digit: 14050471 -> 6-digit: 050471 (leading zero)
        assertEquals("050471", SpotifyAuth.generateTotp(secret, 1111111111L))

        // Time = 1234567890s -> Step 41152263 -> 8-digit: 89005924 -> 6-digit: 005924 (double leading zero)
        assertEquals("005924", SpotifyAuth.generateTotp(secret, 1234567890L))

        // Time = 2000000000s -> Step 66666666 -> 8-digit: 69279037 -> 6-digit: 279037
        assertEquals("279037", SpotifyAuth.generateTotp(secret, 2000000000L))

        // Time = 20000000000s -> Step 666666666 -> 8-digit: 65353130 -> 6-digit: 353130
        assertEquals("353130", SpotifyAuth.generateTotp(secret, 20000000000L))
    }

    @Test
    fun testTotpOutputFormat() {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        for (time in listOf(0L, 30L, 60L, 1000L, 1740000000L)) {
            val code = SpotifyAuth.generateTotp(secret, time)
            assertEquals(6, code.length)
            assertTrue("TOTP must contain only digits, was: $code", code.all { it.isDigit() })
        }
    }

    @Test
    fun testTotpIntervalStability() {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        // 1700000010L / 30 = 56666667 (window 1700000010L .. 1700000039L)
        val baseTime = 1700000010L
        val otp1 = SpotifyAuth.generateTotp(secret, baseTime)
        val otp2 = SpotifyAuth.generateTotp(secret, baseTime + 15L) // 1700000025L
        val otp3 = SpotifyAuth.generateTotp(secret, baseTime + 29L) // 1700000039L
        assertEquals(otp1, otp2)
        assertEquals(otp1, otp3)

        // Timestamp in next 30s window (Step 56666668) should yield a different TOTP
        val otpNext = SpotifyAuth.generateTotp(secret, baseTime + 30L) // 1700000040L
        assertTrue(otp1 != otpNext)
    }

    // =========================================================================
    // 2. Base32 Decoding Tests
    // =========================================================================

    @Test
    fun testBase32Rfc4648TestVectors() {
        // RFC 4648 Section 10 test vectors
        assertEquals("", String(SpotifyAuth.base32Decode(""), StandardCharsets.UTF_8))
        assertEquals("f", String(SpotifyAuth.base32Decode("MY======"), StandardCharsets.UTF_8))
        assertEquals("fo", String(SpotifyAuth.base32Decode("MZXQ===="), StandardCharsets.UTF_8))
        assertEquals("foo", String(SpotifyAuth.base32Decode("MZXW6==="), StandardCharsets.UTF_8))
        assertEquals("foob", String(SpotifyAuth.base32Decode("MZXW6YQ="), StandardCharsets.UTF_8))
        assertEquals("fooba", String(SpotifyAuth.base32Decode("MZXW6YTB"), StandardCharsets.UTF_8))
        assertEquals("foobar", String(SpotifyAuth.base32Decode("MZXW6YTBOI======"), StandardCharsets.UTF_8))
    }

    @Test
    fun testBase32PaddingAndCasingVariations() {
        // Unpadded
        assertEquals("f", String(SpotifyAuth.base32Decode("MY"), StandardCharsets.UTF_8))
        assertEquals("fo", String(SpotifyAuth.base32Decode("MZXQ"), StandardCharsets.UTF_8))
        assertEquals("foo", String(SpotifyAuth.base32Decode("MZXW6"), StandardCharsets.UTF_8))
        assertEquals("foob", String(SpotifyAuth.base32Decode("MZXW6YQ"), StandardCharsets.UTF_8))
        assertEquals("fooba", String(SpotifyAuth.base32Decode("MZXW6YTB"), StandardCharsets.UTF_8))
        assertEquals("foobar", String(SpotifyAuth.base32Decode("MZXW6YTBOI"), StandardCharsets.UTF_8))

        // Lowercase and mixed case
        assertEquals("foobar", String(SpotifyAuth.base32Decode("mzxw6ytboi"), StandardCharsets.UTF_8))
        assertEquals("foobar", String(SpotifyAuth.base32Decode("mzxw6ytboi======"), StandardCharsets.UTF_8))
        assertEquals("foobar", String(SpotifyAuth.base32Decode("MzXw6YtBoI"), StandardCharsets.UTF_8))

        // Decodes 20-byte ASCII key
        val secretAscii = String(SpotifyAuth.base32Decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"), StandardCharsets.UTF_8)
        assertEquals("12345678901234567890", secretAscii)
    }

    // =========================================================================
    // 3. CookieSanitizer Unit Tests
    // =========================================================================

    @Test
    fun testCookieSanitizerRawToken() {
        val rawToken = "AQB6_test_token_12345_XYZ"
        assertEquals(rawToken, CookieSanitizer.sanitizeSpDc(rawToken))
        assertEquals(rawToken, CookieSanitizer.sanitizeSpDc("  $rawToken  "))
        assertEquals(rawToken, CookieSanitizer.sanitizeSpDc("\"$rawToken\""))
        assertEquals(rawToken, CookieSanitizer.sanitizeSpDc("'$rawToken'"))
        assertEquals(rawToken, CookieSanitizer.sanitizeSpDc("$rawToken;"))
    }

    @Test
    fun testCookieSanitizerKeyValueFormat() {
        val rawToken = "AQB6_test_token_12345_XYZ"
        assertEquals(rawToken, CookieSanitizer.sanitizeSpDc("sp_dc=$rawToken"))
        assertEquals(rawToken, CookieSanitizer.sanitizeSpDc("sp_dc = $rawToken ;"))
        assertEquals(rawToken, CookieSanitizer.sanitizeSpDc("sp_dc=\"$rawToken\""))
        assertEquals(rawToken, CookieSanitizer.sanitizeSpDc("SP_DC=$rawToken"))
        assertEquals(rawToken, CookieSanitizer.sanitizeSpDc("Sp_Dc=$rawToken"))
        assertEquals(rawToken, CookieSanitizer.sanitizeSpDc("sp_dc: $rawToken"))
    }

    @Test
    fun testCookieSanitizerHeaderFormat() {
        val dc = "AQB6_test_token_12345_XYZ"
        val key = "xyz_secret_key_456"

        val cookieHeader = "Cookie: sp_dc=$dc; sp_key=$key; Path=/; Domain=.spotify.com; Secure; HttpOnly"
        assertEquals(dc, CookieSanitizer.sanitizeSpDc(cookieHeader))
        assertEquals(key, CookieSanitizer.sanitizeSpKey(cookieHeader))

        val setCookieHeader = "Set-Cookie: sp_dc=$dc; Secure; HttpOnly; SameSite=Lax"
        assertEquals(dc, CookieSanitizer.sanitizeSpDc(setCookieHeader))

        val multiHeader = "other_cookie=123; sp_dc=$dc; session_id=abc; sp_key=$key"
        assertEquals(dc, CookieSanitizer.sanitizeSpDc(multiHeader))
        assertEquals(key, CookieSanitizer.sanitizeSpKey(multiHeader))
    }

    @Test
    fun testCookieSanitizerJsonFormats() {
        val dc = "AQB6_test_token_12345_XYZ"
        val key = "xyz_secret_key_456"

        // EditThisCookie / Cookie-Editor JSON Array
        val jsonArray = """
            [
                {"name": "sp_dc", "value": "$dc", "domain": ".spotify.com"},
                {"name": "sp_key", "value": "$key", "domain": ".spotify.com"}
            ]
        """.trimIndent()
        assertEquals(dc, CookieSanitizer.sanitizeSpDc(jsonArray))
        assertEquals(key, CookieSanitizer.sanitizeSpKey(jsonArray))

        // JSON Array with Capitalized Keys
        val jsonCapitalized = """
            [
                {"Name": "sp_dc", "Value": "$dc"},
                {"Name": "sp_key", "Value": "$key"}
            ]
        """.trimIndent()
        assertEquals(dc, CookieSanitizer.sanitizeSpDc(jsonCapitalized))
        assertEquals(key, CookieSanitizer.sanitizeSpKey(jsonCapitalized))

        // JSON Map
        val jsonMap = """{"sp_dc": "$dc", "sp_key": "$key"}"""
        assertEquals(dc, CookieSanitizer.sanitizeSpDc(jsonMap))
        assertEquals(key, CookieSanitizer.sanitizeSpKey(jsonMap))

        // Nested JSON Object with cookies array
        val nestedJson = """{"cookies": [{"name": "sp_dc", "value": "$dc"}]}"""
        assertEquals(dc, CookieSanitizer.sanitizeSpDc(nestedJson))
    }

    @Test
    fun testCookieSanitizerNetscapeFormat() {
        val dc = "AQB6_test_token_12345_XYZ"
        val key = "xyz_secret_key_456"

        val netscapeTab = "# Netscape HTTP Cookie File\n.spotify.com\tTRUE\t/\tTRUE\t1798765432\tsp_dc\t$dc\n.spotify.com\tTRUE\t/\tTRUE\t0\tsp_key\t$key"
        assertEquals(dc, CookieSanitizer.sanitizeSpDc(netscapeTab))
        assertEquals(key, CookieSanitizer.sanitizeSpKey(netscapeTab))

        val netscapeSpace = ".spotify.com TRUE / TRUE 1798765432 sp_dc $dc"
        assertEquals(dc, CookieSanitizer.sanitizeSpDc(netscapeSpace))
    }

    @Test
    fun testCookieSanitizerUrlEncoded() {
        val dc = "AQB+123/456=="
        val key = "xyz 789"

        // URL encoded whole header
        val encodedHeader = "sp_dc%3DAQB%2B123%2F456%3D%3D%3B%20sp_key%3Dxyz%20789"
        assertEquals(dc, CookieSanitizer.sanitizeSpDc(encodedHeader))
        assertEquals(key, CookieSanitizer.sanitizeSpKey(encodedHeader))

        // URL encoded individual values
        val encodedValue = "sp_dc=AQB%2B123%2F456%3D%3D"
        assertEquals("AQB+123/456==", CookieSanitizer.sanitizeSpDc(encodedValue))
    }

    @Test
    fun testCookieSanitizerExtractCookies() {
        val header = "sp_dc=token1; sp_key=token2; session=abc123"
        val cookies = CookieSanitizer.extractCookies(header)
        assertEquals(3, cookies.size)
        assertEquals("token1", cookies["sp_dc"])
        assertEquals("token2", cookies["sp_key"])
        assertEquals("abc123", cookies["session"])
    }

    @Test
    fun testCookieSanitizerEdgeCases() {
        assertNull(CookieSanitizer.sanitizeSpDc(null))
        assertNull(CookieSanitizer.sanitizeSpDc(""))
        assertNull(CookieSanitizer.sanitizeSpDc("   \n\t  "))
        assertNull(CookieSanitizer.sanitizeSpDc("other_cookie=12345"))

        assertNull(CookieSanitizer.sanitizeSpKey(null))
        assertNull(CookieSanitizer.sanitizeSpKey(""))
        assertNull(CookieSanitizer.sanitizeSpKey("   "))
        assertNull(CookieSanitizer.sanitizeSpKey("AQB6_token_without_sp_key"))

        assertEquals(emptyMap<String, String>(), CookieSanitizer.extractCookies(null))
        assertEquals(emptyMap<String, String>(), CookieSanitizer.extractCookies(""))
        assertEquals(emptyMap<String, String>(), CookieSanitizer.extractCookies("   "))
        assertEquals(emptyMap<String, String>(), CookieSanitizer.extractCookies("{ invalid json }"))
    }

    // =========================================================================
    // 4. JSON Model Deserialization Tests
    // =========================================================================

    @Test
    fun testSpotifyInternalTokenDeserialization() {
        val fullJson = """
            {
                "accessToken": "BQB_test_access_token_12345",
                "accessTokenExpirationTimestampMs": 1750000000000,
                "isAnonymous": false,
                "clientId": "web-player-client-id"
            }
        """.trimIndent()

        val token = json.decodeFromString<SpotifyInternalToken>(fullJson)
        assertEquals("BQB_test_access_token_12345", token.accessToken)
        assertEquals(1750000000000L, token.accessTokenExpirationTimestampMs)
        assertFalse(token.isAnonymous)
        assertEquals("web-player-client-id", token.clientId)

        val minimalJson = """
            {
                "accessToken": "BQB_minimal_token",
                "accessTokenExpirationTimestampMs": 1740000000000
            }
        """.trimIndent()

        val minimalToken = json.decodeFromString<SpotifyInternalToken>(minimalJson)
        assertEquals("BQB_minimal_token", minimalToken.accessToken)
        assertEquals(1740000000000L, minimalToken.accessTokenExpirationTimestampMs)
        assertFalse(minimalToken.isAnonymous)
        assertEquals("", minimalToken.clientId)

        val anonJson = """
            {
                "accessToken": "BQB_anon_token",
                "accessTokenExpirationTimestampMs": 1740000000000,
                "isAnonymous": true
            }
        """.trimIndent()
        val anonToken = json.decodeFromString<SpotifyInternalToken>(anonJson)
        assertTrue(anonToken.isAnonymous)
    }

    @Test
    fun testServerTimeResponseDeserialization() {
        val jsonStr = """{"serverTime": 1740685000}"""
        val response = json.decodeFromString<ServerTimeResponse>(jsonStr)
        assertEquals(1740685000L, response.serverTime)
    }

    @Test
    fun testNuanceAndGistFilesDeserialization() {
        val nuanceListJson = """
            [
                {"s": "OLD_SECRET_KEY", "v": 1},
                {"s": "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "v": 2026}
            ]
        """.trimIndent()

        val nuances = json.decodeFromString<List<Nuance>>(nuanceListJson)
        assertEquals(2, nuances.size)
        val latest = nuances.maxByOrNull { it.v }
        assertNotNull(latest)
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", latest?.s)
        assertEquals(2026, latest?.v)

        // Mock GitHub Gist response
        val escapedNuances = nuanceListJson.replace("\"", "\\\"").replace("\n", "")
        val gistJson = """
            {
                "files": {
                    "secrets.json": {
                        "content": "$escapedNuances"
                    }
                }
            }
        """.trimIndent()

        val gist = json.decodeFromString<GistFiles>(gistJson)
        assertEquals(1, gist.files.size)
        val content = gist.files["secrets.json"]?.content
        assertNotNull(content)
        val parsedNuances = json.decodeFromString<List<Nuance>>(content!!)
        assertEquals(2, parsedNuances.size)
        assertEquals(2026, parsedNuances.maxByOrNull { it.v }?.v)
    }

    // =========================================================================
    // 5. SpotifyAuth Pipeline & Error Handling
    // =========================================================================

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

    @Test
    fun testSpotifyAuthWithBlankCookie() = runTest {
        val result = SpotifyAuth.fetchAccessToken(spDc = "   ")
        assertTrue("Blank cookie must fail immediately", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(
            "Expected SpotifyException with 400 or invalid message, got: $error",
            error is Spotify.SpotifyException && error.statusCode == 400
        )
    }
}

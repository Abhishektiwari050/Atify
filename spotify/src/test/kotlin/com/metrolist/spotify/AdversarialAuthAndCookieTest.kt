package com.metrolist.spotify

import com.metrolist.spotify.SpotifyAuth.Nuance
import com.metrolist.spotify.models.SpotifyInternalToken
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * Adversarial and Stress Test Suite for CookieSanitizer and SpotifyAuth.
 * Validates resilience against malformed inputs, edge cases, key variations,
 * and RFC 6238 TOTP boundary conditions.
 */
class AdversarialAuthAndCookieTest {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    // =========================================================================
    // 1. CookieSanitizer: Malformed JSON Stress Tests
    // =========================================================================

    @Test
    fun testMalformedJsonDoesNotCrash() {
        val malformedCases = listOf(
            "[{\"name\": \"sp_dc\", \"value\": \"AQB123\"",
            "{\"sp_dc\": \"AQB123\",}",
            "{sp_dc: \"AQB123\"}",
            "[{\"name\": null, \"value\": \"AQB123\"}]",
            "[{\"name\": \"sp_dc\"}]",
            "[{}]",
            "[]",
            "{}",
            "{\"cookies\": \"not an array\"}",
            "{\"sp_dc\": 123456789}",
            "{\"sp_dc\": true}",
            "{\"sp_dc\": {\"token\": \"nested\"}}",
            "{\"cookies\": [{\"Name\": \"sp_dc\", \"Value\": 9999}]}",
            "[[[[]]]]",
            "{ \"a\": { \"b\": { \"c\": 123 } } }",
            "{\"sp_dc\": \"\"}",
            "{\"sp_dc\": \"   \"}"
        )

        for (case in malformedCases) {
            val cookies = CookieSanitizer.extractCookies(case)
            assertNotNull(cookies)
            val spDc = CookieSanitizer.sanitizeSpDc(case)
            if (case.contains("123456789") && spDc != null) {
                assertEquals("123456789", spDc)
            }
        }
    }

    @Test
    fun testJsonAlternativeKeyNames() {
        val dcVal = "AQB_KEY_VARIATION_TOKEN"
        val keyVal = "XYZ_KEY_VARIATION_TOKEN"

        val jsonKeyVal = """[{"key": "sp_dc", "value": "$dcVal"}, {"key": "sp_key", "value": "$keyVal"}]"""
        assertEquals(dcVal, CookieSanitizer.sanitizeSpDc(jsonKeyVal))
        assertEquals(keyVal, CookieSanitizer.sanitizeSpKey(jsonKeyVal))

        val jsonKeyValPascal = """[{"Key": "sp_dc", "Value": "$dcVal"}, {"Key": "sp_key", "Value": "$keyVal"}]"""
        assertEquals(dcVal, CookieSanitizer.sanitizeSpDc(jsonKeyValPascal))
        assertEquals(keyVal, CookieSanitizer.sanitizeSpKey(jsonKeyValPascal))

        val jsonUpperCase = """{"SP_DC": "$dcVal", "SP_KEY": "$keyVal"}"""
        assertEquals(dcVal, CookieSanitizer.sanitizeSpDc(jsonUpperCase))
        assertEquals(keyVal, CookieSanitizer.sanitizeSpKey(jsonUpperCase))
    }

    // =========================================================================
    // 2. CookieSanitizer: Mixed Quotes, Spaces, and Semicolons
    // =========================================================================

    @Test
    fun testQuotesAndDelimiters() {
        val token = "AQB_CLEAN_TOKEN_999"

        assertEquals(token, CookieSanitizer.sanitizeSpDc("'$token'"))
        assertEquals(token, CookieSanitizer.sanitizeSpDc("\"$token\""))
        assertEquals(token, CookieSanitizer.sanitizeSpDc("  \"$token\"  "))
        assertEquals(token, CookieSanitizer.sanitizeSpDc("  '$token'  "))
        assertEquals(token, CookieSanitizer.sanitizeSpDc("\"$token\";"))
        assertEquals(token, CookieSanitizer.sanitizeSpDc("'$token';"))

        assertEquals(token, CookieSanitizer.sanitizeSpDc("sp_dc=\"$token\""))
        assertEquals(token, CookieSanitizer.sanitizeSpDc("sp_dc='$token'"))
        assertEquals(token, CookieSanitizer.sanitizeSpDc("\"sp_dc\"=\"$token\""))
        assertEquals(token, CookieSanitizer.sanitizeSpDc("'sp_dc'='$token'"))
        assertEquals(token, CookieSanitizer.sanitizeSpDc("\"sp_dc\"='$token'"))
        assertEquals(token, CookieSanitizer.sanitizeSpDc("sp_dc=\"$token\"; Path=/; Domain=.spotify.com"))

        assertEquals(token, CookieSanitizer.sanitizeSpDc("sp_dc: \"$token\""))
        assertEquals(token, CookieSanitizer.sanitizeSpDc("sp_dc: '$token'"))
    }

    @Test
    fun testInvalidOrMalformedQuoteInputs() {
        val token = "AQB_MISMATCHED"
        val mismatched1 = "'$token\""
        val result1 = CookieSanitizer.sanitizeSpDc(mismatched1)
        assertNotNull(result1)

        assertNull(CookieSanitizer.sanitizeSpDc("AQB token with spaces"))
        assertNull(CookieSanitizer.sanitizeSpDc("foo bar baz"))
    }

    // =========================================================================
    // 3. CookieSanitizer: URL Encoding & Exotic Special Characters
    // =========================================================================

    @Test
    fun testUrlEncodedTokensWithBase64Characters() {
        val complexToken = "AQB+Alpha/Beta==Gamma"
        val encodedToken = "AQB%2BAlpha%2FBeta%3D%3DGamma"

        assertEquals(complexToken, CookieSanitizer.sanitizeSpDc("sp_dc=$encodedToken"))

        val fullEncoded = "sp_dc%3DAQB%2BAlpha%2FBeta%3D%3DGamma%3B%20domain%3D.spotify.com"
        assertEquals(complexToken, CookieSanitizer.sanitizeSpDc(fullEncoded))

        assertEquals(complexToken, CookieSanitizer.sanitizeSpDc(encodedToken))
    }

    @Test
    fun testBrokenUrlEncodingDoesNotThrow() {
        val broken1 = "AQB%2"
        val broken2 = "sp_dc=AQB%ZZ_invalid"
        val broken3 = "%"
        val broken4 = "%%%"

        assertEquals("AQB%2", CookieSanitizer.sanitizeSpDc(broken1))
        assertEquals("AQB%ZZ_invalid", CookieSanitizer.sanitizeSpDc(broken2))
        assertEquals("%", CookieSanitizer.sanitizeSpDc(broken3))
        assertEquals("%%%", CookieSanitizer.sanitizeSpDc(broken4))
    }

    // =========================================================================
    // 4. CookieSanitizer: Netscape Format Variations & Malformed Rows
    // =========================================================================

    @Test
    fun testNetscapeFormatResilience() {
        val dc = "AQB_NETSCAPE_DC"
        val key = "XYZ_NETSCAPE_KEY"

        val standardNetscape = """
            # Netscape HTTP Cookie File
            # http://curl.haxx.se/rfc/cookie_spec.html
            .spotify.com	TRUE	/	TRUE	1893456000	sp_dc	$dc
            .spotify.com	TRUE	/	FALSE	1893456000	sp_key	$key
        """.trimIndent()
        assertEquals(dc, CookieSanitizer.sanitizeSpDc(standardNetscape))
        assertEquals(key, CookieSanitizer.sanitizeSpKey(standardNetscape))

        val spaceNetscape = ".spotify.com   TRUE   /   TRUE   1893456000   sp_dc   $dc"
        assertEquals(dc, CookieSanitizer.sanitizeSpDc(spaceNetscape))

        val httpOnlyNetscape = """
            #HttpOnly_.spotify.com	TRUE	/	TRUE	1893456000	sp_dc	$dc
        """.trimIndent()
        CookieSanitizer.sanitizeSpDc(httpOnlyNetscape)

        val shortRow = ".spotify.com TRUE / sp_dc $dc"
        assertNull(CookieSanitizer.sanitizeSpDc(shortRow))

        val badExpRow = ".spotify.com	TRUE	/	TRUE	INVALID_EXP	sp_dc	$dc"
        assertNull(CookieSanitizer.sanitizeSpDc(badExpRow))

        val noiseNetscape = """
            # Comment 1

            # Comment 2
            .spotify.com	TRUE	/	TRUE	1893456000	sp_dc	$dc

        """.trimIndent()
        assertEquals(dc, CookieSanitizer.sanitizeSpDc(noiseNetscape))
    }

    // =========================================================================
    // 5. CookieSanitizer: Exotic Header Prefixes & Casing
    // =========================================================================

    @Test
    fun testExoticPrefixesAndCasing() {
        val dc = "AQB_EXOTIC_PREFIX"

        assertEquals(dc, CookieSanitizer.sanitizeSpDc("Cookie: sp_dc=$dc"))
        assertEquals(dc, CookieSanitizer.sanitizeSpDc("COOKIE: sp_dc=$dc"))
        assertEquals(dc, CookieSanitizer.sanitizeSpDc("cookie: sp_dc=$dc"))
        assertEquals(dc, CookieSanitizer.sanitizeSpDc("Set-Cookie: sp_dc=$dc; Path=/"))
        assertEquals(dc, CookieSanitizer.sanitizeSpDc("SET-COOKIE: sp_dc=$dc; Path=/"))
        assertEquals(dc, CookieSanitizer.sanitizeSpDc("set-cookie: sp_dc=$dc; Path=/"))

        assertEquals(dc, CookieSanitizer.sanitizeSpDc("SP_DC=$dc"))
        assertEquals(dc, CookieSanitizer.sanitizeSpDc("Sp_Dc=$dc"))
        assertEquals(dc, CookieSanitizer.sanitizeSpDc("sP_dC=$dc"))

        val fullHeader = "sp_dc=$dc; Domain=.spotify.com; Path=/; Expires=Wed, 21 Oct 2026 07:28:00 GMT; Max-Age=3600; Secure; HttpOnly; SameSite=Lax; Priority=High"
        val cookies = CookieSanitizer.extractCookies(fullHeader)
        assertEquals(1, cookies.size)
        assertEquals(dc, cookies["sp_dc"])
        assertNull(cookies["path"])
        assertNull(cookies["domain"])
        assertNull(cookies["expires"])
        assertNull(cookies["max-age"])
        assertNull(cookies["samesite"])
        assertNull(cookies["priority"])
    }

    // =========================================================================
    // 6. SpotifyAuth & RFC 6238 TOTP: Boundary Timestamps & Key Variations
    // =========================================================================

    @Test
    fun testRfc6238BoundaryTimestamps() {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

        val totpEpoch0 = SpotifyAuth.generateTotp(secret, 0L)
        assertEquals(6, totpEpoch0.length)
        assertTrue(totpEpoch0.all { it.isDigit() })

        val totpEpoch29 = SpotifyAuth.generateTotp(secret, 29L)
        assertEquals(totpEpoch0, totpEpoch29)

        val totpStep1 = SpotifyAuth.generateTotp(secret, 30L)
        assertEquals(6, totpStep1.length)
        assertEquals("287082", SpotifyAuth.generateTotp(secret, 59L))
        assertEquals("287082", totpStep1)

        val totpInt32Max = SpotifyAuth.generateTotp(secret, 2147483647L)
        assertEquals(6, totpInt32Max.length)
        assertTrue(totpInt32Max.all { it.isDigit() })

        val totpUint32Max = SpotifyAuth.generateTotp(secret, 4294967295L)
        assertEquals(6, totpUint32Max.length)
        assertTrue(totpUint32Max.all { it.isDigit() })

        val totpFarFuture = SpotifyAuth.generateTotp(secret, 100000000000L)
        assertEquals(6, totpFarFuture.length)
        assertTrue(totpFarFuture.all { it.isDigit() })
    }

    @Test
    fun testBase32DecodingRobustness() {
        val standard = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

        val decoded1 = SpotifyAuth.base32Decode(standard)
        val decoded2 = SpotifyAuth.base32Decode(standard + "======")
        val decoded3 = SpotifyAuth.base32Decode(standard.lowercase())
        val decoded4 = SpotifyAuth.base32Decode("GeZdGnBvGy3TqOjQgEzDgNbVgY3TqOjQ")

        val expected = "12345678901234567890".toByteArray(StandardCharsets.UTF_8)
        assertTrue(expected.contentEquals(decoded1))
        assertTrue(expected.contentEquals(decoded2))
        assertTrue(expected.contentEquals(decoded3))
        assertTrue(expected.contentEquals(decoded4))

        assertTrue(SpotifyAuth.base32Decode("").isEmpty())

        val formatted = "GEZD-GNBV-GY3T-QOJQ-GEZD-GNBV-GY3T-QOJQ"
        val decodedFormatted = SpotifyAuth.base32Decode(formatted)
        assertTrue(expected.contentEquals(decodedFormatted))
    }

    @Test
    fun testTotpLeadingZeroIntegrity() {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

        val otp1 = SpotifyAuth.generateTotp(secret, 1111111109L)
        assertEquals("081804", otp1)
        assertTrue(otp1.startsWith("0"))

        val otp2 = SpotifyAuth.generateTotp(secret, 1111111111L)
        assertEquals("050471", otp2)
        assertTrue(otp2.startsWith("0"))

        val otp3 = SpotifyAuth.generateTotp(secret, 1234567890L)
        assertEquals("005924", otp3)
        assertTrue(otp3.startsWith("00"))
    }

    @Test
    fun testNuanceVersionSortingResilience() {
        val nuancesJson = """
            [
                {"s": "SECRET_V1", "v": 1},
                {"s": "SECRET_V100", "v": 100},
                {"s": "SECRET_V50", "v": 50},
                {"s": "SECRET_V2026", "v": 2026},
                {"s": "SECRET_V10", "v": 10}
            ]
        """.trimIndent()

        val nuances = json.decodeFromString<List<Nuance>>(nuancesJson)
        val highest = nuances.maxByOrNull { it.v }
        assertNotNull(highest)
        assertEquals(2026, highest?.v)
        assertEquals("SECRET_V2026", highest?.s)
    }
}


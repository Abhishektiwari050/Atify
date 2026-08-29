package com.metrolist.spotify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Multi-format cookie parser and sanitization engine for Spotify authentication.
 *
 * Supports parsing and sanitizing tokens from:
 * - Raw token strings (e.g., "AQB...")
 * - Key-value strings (e.g., "sp_dc=AQB...", "sp_key=xyz")
 * - HTTP Cookie / Set-Cookie headers (e.g., "Cookie: sp_dc=AQB...; sp_key=xyz; Path=/; Domain=.spotify.com")
 * - Browser JSON cookie exports (e.g., EditThisCookie, Cookie-Editor arrays, or key-value objects)
 * - Netscape / curl cookie file format (.spotify.com\tTRUE\t/\tTRUE\t0\tsp_dc\tAQB...)
 * - URL-encoded strings and values (e.g., "sp_dc%3DAQB..." or "sp_dc=AQB%2B123%3D%3D")
 * - Single/double quoted strings (e.g., "\"AQB...\"", "sp_dc='AQB...'")
 */
object CookieSanitizer {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    /**
     * Extracts and sanitizes the `sp_dc` cookie value from any supported input format.
     *
     * @param rawInput Raw input string from clipboard, text field, or header.
     * @return Sanitized `sp_dc` token, or null if input is blank or contains no valid sp_dc.
     */
    fun sanitizeSpDc(rawInput: String?): String? {
        if (rawInput.isNullOrBlank()) return null
        val trimmed = rawInput.trim()

        // 1. Try extracting cookies map
        val cookies = extractCookies(trimmed)
        val extracted = cookies["sp_dc"] ?: cookies.entries.firstOrNull {
            it.key.equals("sp_dc", ignoreCase = true)
        }?.value

        if (!extracted.isNullOrBlank()) {
            return cleanValue(extracted)
        }

        // 2. If not found in cookie map, check if input itself is a raw token or prefixed with sp_dc
        return extractRawTokenCandidate(trimmed, targetName = "sp_dc", requirePrefix = false)
    }

    /**
     * Extracts and sanitizes the `sp_key` cookie value from any supported input format.
     *
     * @param rawInput Raw input string.
     * @return Sanitized `sp_key` token, or null if sp_key is not present or blank.
     */
    fun sanitizeSpKey(rawInput: String?): String? {
        if (rawInput.isNullOrBlank()) return null
        val trimmed = rawInput.trim()

        // 1. Try extracting cookies map
        val cookies = extractCookies(trimmed)
        val extracted = cookies["sp_key"] ?: cookies.entries.firstOrNull {
            it.key.equals("sp_key", ignoreCase = true)
        }?.value

        if (!extracted.isNullOrBlank()) {
            return cleanValue(extracted)
        }

        // 2. Check if prefixed with sp_key=
        return extractRawTokenCandidate(trimmed, targetName = "sp_key", requirePrefix = true)
    }

    /**
     * Parses arbitrary cookie inputs into a structured Map of cookie names to sanitized values.
     *
     * @param rawInput Raw input string.
     * @return Map of cookie name to cookie value.
     */
    fun extractCookies(rawInput: String?): Map<String, String> {
        if (rawInput.isNullOrBlank()) return emptyMap()
        val trimmed = rawInput.trim()

        // 1. Try JSON formats (JSON Array or JSON Object)
        if ((trimmed.startsWith("[") && trimmed.endsWith("]")) ||
            (trimmed.startsWith("{") && trimmed.endsWith("}"))
        ) {
            val jsonCookies = parseJsonCookies(trimmed)
            if (jsonCookies.isNotEmpty()) {
                return jsonCookies
            }
        }

        // 2. Try Netscape / curl format (multiline or single line matching Netscape spec)
        val netscapeCookies = parseNetscapeCookies(trimmed)
        if (netscapeCookies.isNotEmpty()) {
            return netscapeCookies
        }

        // 3. Try standard Key-Value or Cookie / Set-Cookie Header formats
        val headerCookies = parseHeaderCookies(trimmed)
        if (headerCookies.isNotEmpty()) {
            return headerCookies
        }

        // 4. Try URL-decoded parsing if string is percent-encoded
        if (trimmed.contains("%")) {
            val decoded = safeUrlDecode(trimmed)
            if (decoded != trimmed) {
                val decodedCookies = extractCookies(decoded)
                if (decodedCookies.isNotEmpty()) {
                    return decodedCookies
                }
            }
        }

        return emptyMap()
    }

    private fun parseJsonCookies(input: String): Map<String, String> {
        return try {
            val result = mutableMapOf<String, String>()
            val element = json.parseToJsonElement(input)
            when (element) {
                is JsonArray -> {
                    for (item in element) {
                        if (item is JsonObject) {
                            val name = item["name"]?.let { (it as? JsonPrimitive)?.content }
                                ?: item["Name"]?.let { (it as? JsonPrimitive)?.content }
                                ?: item["key"]?.let { (it as? JsonPrimitive)?.content }
                                ?: item["Key"]?.let { (it as? JsonPrimitive)?.content }

                            val value = item["value"]?.let { (it as? JsonPrimitive)?.content }
                                ?: item["Value"]?.let { (it as? JsonPrimitive)?.content }

                            if (!name.isNullOrBlank() && value != null) {
                                result[cleanKey(name)] = cleanValue(value)
                            }
                        }
                    }
                }
                is JsonObject -> {
                    val cookiesArray = element["cookies"] as? JsonArray
                        ?: element["Cookies"] as? JsonArray
                    if (cookiesArray != null) {
                        for (item in cookiesArray) {
                            if (item is JsonObject) {
                                val name = item["name"]?.let { (it as? JsonPrimitive)?.content }
                                    ?: item["Name"]?.let { (it as? JsonPrimitive)?.content }
                                    ?: item["key"]?.let { (it as? JsonPrimitive)?.content }
                                    ?: item["Key"]?.let { (it as? JsonPrimitive)?.content }

                                val value = item["value"]?.let { (it as? JsonPrimitive)?.content }
                                    ?: item["Value"]?.let { (it as? JsonPrimitive)?.content }

                                if (!name.isNullOrBlank() && value != null) {
                                    result[cleanKey(name)] = cleanValue(value)
                                }
                            }
                        }
                    } else {
                        for ((k, v) in element) {
                            if (v is JsonPrimitive) {
                                result[cleanKey(k)] = cleanValue(v.content)
                            }
                        }
                    }
                }
                else -> Unit
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parseNetscapeCookies(input: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val lines = input.lines()
        for (line in lines) {
            val cleanLine = line.trim()
            if (cleanLine.isBlank() || cleanLine.startsWith("#")) continue

            val tokens = if (cleanLine.contains('\t')) {
                cleanLine.split('\t').map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                cleanLine.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
            }

            if (isNetscapeRow(tokens)) {
                val name = tokens[5]
                val value = tokens[6]
                if (name.isNotEmpty()) {
                    result[cleanKey(name)] = cleanValue(value)
                }
            }
        }
        return result
    }

    private fun isNetscapeRow(tokens: List<String>): Boolean {
        if (tokens.size != 7) return false
        val flag1 = tokens[1].equals("TRUE", ignoreCase = true) || tokens[1].equals("FALSE", ignoreCase = true)
        val path = tokens[2].startsWith("/")
        val flag2 = tokens[3].equals("TRUE", ignoreCase = true) || tokens[3].equals("FALSE", ignoreCase = true)
        val expNumeric = tokens[4].toLongOrNull() != null
        return flag1 && path && flag2 && expNumeric
    }

    private fun parseHeaderCookies(input: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var stripped = input.trim()
        val prefixes = listOf("Cookie:", "cookie:", "Set-Cookie:", "set-cookie:", "Set-cookie:")
        for (prefix in prefixes) {
            if (stripped.startsWith(prefix, ignoreCase = true)) {
                stripped = stripped.substring(prefix.length).trim()
                break
            }
        }

        // Split by semicolons or newlines
        val pairs = stripped.split(';', '\n', '\r')
        for (pair in pairs) {
            val trimmedPair = pair.trim()
            if (trimmedPair.isBlank()) continue
            val equalIdx = trimmedPair.indexOf('=')
            if (equalIdx > 0) {
                val rawName = trimmedPair.substring(0, equalIdx).trim()
                val rawVal = trimmedPair.substring(equalIdx + 1).trim()
                val name = cleanKey(rawName)
                val value = cleanValue(rawVal)
                val isStandardAttribute = listOf("path", "domain", "samesite", "max-age", "expires", "priority")
                    .contains(name.lowercase())
                if (name.isNotEmpty() && !isStandardAttribute) {
                    result[name] = value
                }
            }
        }
        return result
    }

    private fun extractRawTokenCandidate(
        input: String,
        targetName: String,
        requirePrefix: Boolean
    ): String? {
        var text = stripQuotes(input.trim())

        // Check if prefixed with targetName + "=" or targetName + ":"
        if (text.startsWith("$targetName=", ignoreCase = true)) {
            text = text.substring(targetName.length + 1).trim()
            return cleanValue(text).ifBlank { null }
        }
        if (text.startsWith("$targetName:", ignoreCase = true)) {
            text = text.substring(targetName.length + 1).trim()
            return cleanValue(text).ifBlank { null }
        }

        if (requirePrefix) {
            return null
        }

        // Clean trailing semicolon if present
        if (text.endsWith(";")) {
            text = text.dropLast(1).trim()
        }
        text = stripQuotes(text)

        // If targetName is sp_dc and no prefix was required, verify this is not another cookie assignment or multipart header
        if (text.contains("=") || text.contains(";") || text.contains("\t") || text.contains("\n") || text.contains(" ")) {
            return null
        }

        val cleaned = cleanValue(text)
        return if (cleaned.isNotBlank()) cleaned else null
    }

    private fun cleanValue(value: String): String {
        var clean = stripQuotes(value.trim())
        if (clean.endsWith(";")) {
            clean = clean.dropLast(1).trim()
        }
        if (clean.contains("%")) {
            clean = safeUrlDecode(clean)
        }
        clean = stripQuotes(clean.trim())
        return clean
    }

    private fun cleanKey(key: String): String {
        return stripQuotes(key.trim())
    }

    private fun stripQuotes(input: String): String {
        val trimmed = input.trim()
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length >= 2)
        ) {
            return trimmed.substring(1, trimmed.length - 1).trim()
        }
        return trimmed
    }

    private fun safeUrlDecode(input: String): String {
        return try {
            val protectedInput = input.replace("+", "%2B")
            URLDecoder.decode(protectedInput, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            input
        }
    }
}

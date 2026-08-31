package io.github.sekademi.spotufi.data.api

import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object JioSaavnResolver {
    private const val TAG = "JioSaavnResolver"
    private const val DES_KEY = "38346591"
    private const val SEARCH_URL = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&n=5&p=1&q="

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    data class SaavnTrack(
        val streamUrl: String,
        val title: String,
        val artist: String,
        val quality: String = "AAC 320 kbps",
        val durationSec: Int = 0,
    )

    fun decryptMediaUrl(encryptedUrl: String): String? = runCatching {
        val keySpec = SecretKeySpec(DES_KEY.toByteArray(Charsets.UTF_8), "DES")
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
        val decoded = Base64.decode(encryptedUrl.trim(), Base64.DEFAULT)
        val decrypted = cipher.doFinal(decoded)
        val rawUrl = String(decrypted, Charsets.UTF_8).trim()
        rawUrl.replace("_96.mp4", "_320.mp4")
            .replace("_160.mp4", "_320.mp4")
            .replace("_96.m4a", "_320.m4a")
            .replace("_160.m4a", "_320.m4a")
            .replace("http://", "https://")
    }.onFailure { Log.w(TAG, "DES decrypt failed for media URL", it) }.getOrNull()

    fun resolve(searchText: String, expectedDurationMs: Int = 0): SaavnTrack? {
        val cleanQuery = searchText.trim()
        if (cleanQuery.isBlank()) return null
        return runCatching {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val request = Request.Builder()
                .url(SEARCH_URL + encoded)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body.string()
            if (body.isBlank()) return null

            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return null
            if (results.length() == 0) return null

            fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
            val qn = norm(cleanQuery)

            var bestTrack: SaavnTrack? = null
            var bestScore = -1

            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val rawTitle = item.optString("title", "")
                    .replace("&quot;", "\"")
                    .replace("&#039;", "'")
                    .replace("&amp;", "&")
                val rawSinger = item.optString("subtitle", "")
                    .replace("&quot;", "\"")
                    .replace("&#039;", "'")
                    .replace("&amp;", "&")
                val moreInfo = item.optJSONObject("more_info") ?: continue
                val encUrl = moreInfo.optString("encrypted_media_url", "")
                if (encUrl.isBlank()) continue

                val durationSec = moreInfo.optString("duration", "0").toIntOrNull() ?: 0
                val streamUrl = decryptMediaUrl(encUrl) ?: continue

                // Scorer
                var score = 0
                val titleNorm = norm(rawTitle)
                if (titleNorm.isNotEmpty() && (qn.contains(titleNorm) || titleNorm.contains(qn))) {
                    score += 5
                }
                val singerNorm = norm(rawSinger)
                if (singerNorm.isNotEmpty() && qn.contains(singerNorm)) {
                    score += 3
                }
                if (expectedDurationMs > 0 && durationSec > 0) {
                    val expSec = expectedDurationMs / 1000
                    if (kotlin.math.abs(durationSec - expSec) <= 4) {
                        score += 4
                    }
                }

                if (score > bestScore) {
                    bestScore = score
                    val is320 = moreInfo.optString("320kbps", "true").equals("true", ignoreCase = true)
                    val qualityStr = if (is320) "AAC 320 kbps" else "AAC 160 kbps"
                    bestTrack = SaavnTrack(
                        streamUrl = streamUrl,
                        title = rawTitle,
                        artist = rawSinger,
                        quality = qualityStr,
                        durationSec = durationSec,
                    )
                }
            }

            bestTrack
        }.onFailure { Log.w(TAG, "JioSaavn resolve failed for: $searchText", it) }.getOrNull()
    }
}

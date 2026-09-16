package com.metrolist.spotify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SpotiFlacExtractionTest {

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    @Test
    fun testSonglinkHtmlNextDataParsing() {
        val sampleHtml = """
            <!DOCTYPE html><html><head></head><body>
            <script id="__NEXT_DATA__" type="application/json">
            {
              "props": {
                "pageProps": {
                  "pageData": {
                    "entityData": {
                      "provider": "spotify",
                      "id": "4cOdK2wGLETKBW3PvgPWqT",
                      "title": "Never Gonna Give You Up",
                      "artistName": "Rick Astley",
                      "isrc": "GBARL9300135"
                    },
                    "sections": [
                      {
                        "sectionId": "section|auto|links|listen",
                        "links": [
                          {
                            "displayName": "TIDAL",
                            "platform": "tidal",
                            "uniqueId": "tidal|song|491206012",
                            "url": "https://listen.tidal.com/track/491206012"
                          },
                          {
                            "displayName": "Amazon Music",
                            "platform": "amazonMusic",
                            "uniqueId": "amazon|song|B0DZTSF3YS",
                            "url": "https://music.amazon.com/albums/B0DZTQLXGX?trackAsin=B0DZTSF3YS"
                          },
                          {
                            "displayName": "Deezer",
                            "platform": "deezer",
                            "uniqueId": "deezer|song|781592622"
                          }
                        ]
                      }
                    ]
                  }
                }
              }
            }
            </script></body></html>
        """.trimIndent()

        val jsonStr = sampleHtml.substringAfter("<script id=\"__NEXT_DATA__\" type=\"application/json\">", "")
            .substringBefore("</script>", "")
        assertNotNull(jsonStr)

        val root = json.parseToJsonElement(jsonStr).let { it as JsonObject }
        val pageData = (root["props"] as? JsonObject)
            ?.get("pageProps")?.let { it as? JsonObject }
            ?.get("pageData")?.let { it as? JsonObject }
        assertNotNull(pageData)

        val entityData = pageData?.get("entityData")?.let { it as? JsonObject }
        val isrc = entityData?.get("isrc")?.jsonPrimitive?.content
        assertEquals("GBARL9300135", isrc)

        val sections = pageData?.get("sections") as? JsonArray
        val listenLinks = sections?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it["sectionId"]?.jsonPrimitive?.content?.contains("links|listen") == true }
            ?.get("links") as? JsonArray
        assertNotNull(listenLinks)

        var tidalId: String? = null
        var amazonId: String? = null

        listenLinks?.mapNotNull { it as? JsonObject }?.forEach { link ->
            val platform = link["platform"]?.jsonPrimitive?.content
            val uniqueId = link["uniqueId"]?.jsonPrimitive?.content
            val url = link["url"]?.jsonPrimitive?.content
            when (platform) {
                "tidal" -> {
                    tidalId = uniqueId?.substringAfterLast('|')?.takeIf { it.isNotBlank() }
                        ?: url?.substringAfterLast("/track/")?.substringBefore('?')
                }
                "amazonMusic" -> {
                    amazonId = uniqueId?.substringAfterLast('|')?.takeIf { it.isNotBlank() }
                        ?: url?.substringAfterLast("trackAsin=")?.substringBefore('&')
                }
            }
        }

        assertEquals("491206012", tidalId)
        assertEquals("B0DZTSF3YS", amazonId)
    }
}

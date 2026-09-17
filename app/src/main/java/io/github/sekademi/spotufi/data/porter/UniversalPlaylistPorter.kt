package io.github.sekademi.spotufi.data.porter

import android.content.Context
import io.github.sekademi.spotufi.data.entity.SongsModel
import io.github.sekademi.spotufi.data.playlist.LocalPlaylist
import io.github.sekademi.spotufi.data.playlist.LocalPlaylistManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class PorterTrack(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val coverUri: String = ""
)

data class PorterResult(
    val playlistTitle: String,
    val sourcePlatform: String,
    val coverUri: String,
    val tracks: List<PorterTrack>
)

/**
 * Universal Playlist Porter:
 * Imports public playlists from Apple Music, YouTube / YouTube Music, and Deezer,
 * converts them into native local playlists, and stores them in LocalPlaylistManager.
 */
object UniversalPlaylistPorter {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val YT_PLAYLIST_PATTERN = Pattern.compile("(?:list=)([a-zA-Z0-9_-]+)")
    private val DEEZER_PLAYLIST_PATTERN = Pattern.compile("deezer\\.com/(?:[a-z]{2}/)?playlist/(\\d+)")
    private val APPLE_MUSIC_PATTERN = Pattern.compile("music\\.apple\\.com/([a-z]{2})/playlist/[^/]+/([a-zA-Z0-9.]+)")

    suspend fun importPlaylistFromUrl(context: Context, url: String): Result<LocalPlaylist> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = url.trim()
            val porterResult = when {
                cleanUrl.contains("youtube.com") || cleanUrl.contains("youtu.be") -> {
                    parseYouTubePlaylist(cleanUrl)
                }
                cleanUrl.contains("deezer.com") -> {
                    parseDeezerPlaylist(cleanUrl)
                }
                cleanUrl.contains("apple.com") -> {
                    parseAppleMusicPlaylist(cleanUrl)
                }
                else -> {
                    return@withContext Result.failure(IllegalArgumentException("Unsupported link format. Please provide an Apple Music, YouTube Music, or Deezer playlist URL."))
                }
            }

            if (porterResult.tracks.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No tracks found in the playlist."))
            }

            // Convert PorterTracks to SongsModels
            val songModels = porterResult.tracks.map { track ->
                SongsModel(
                    id = (track.title + track.artist).hashCode(),
                    title = track.title,
                    singer = track.artist,
                    album = track.album,
                    coverUri = track.coverUri.ifBlank { porterResult.coverUri },
                    url = "", // StreamResolver will resolve on demand
                    durationMs = track.durationMs.toInt(),
                    explicit = false
                )
            }

            // Create local playlist
            val playlist = LocalPlaylistManager.createPlaylist(
                context = context,
                title = porterResult.playlistTitle.ifBlank { "Imported ${porterResult.sourcePlatform} Playlist" },
                description = "Imported from ${porterResult.sourcePlatform} via Universal Porter",
                initialSong = songModels.firstOrNull()
            )

            // Add remaining songs
            for (i in 1 until songModels.size) {
                LocalPlaylistManager.addSongToPlaylist(context, playlist.id, songModels[i])
            }

            val finalPlaylist = LocalPlaylistManager.getPlaylist(context, playlist.id) ?: playlist
            Result.success(finalPlaylist)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseYouTubePlaylist(url: String): PorterResult {
        val matcher = YT_PLAYLIST_PATTERN.matcher(url)
        if (!matcher.find()) {
            throw IllegalArgumentException("Invalid YouTube playlist link: missing list parameter.")
        }
        val playlistId = matcher.group(1) ?: throw IllegalArgumentException("Could not extract playlist ID.")

        val doc = Jsoup.connect("https://www.youtube.com/playlist?list=$playlistId")
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .timeout(15000)
            .get()

        val rawTitle = doc.title().replace(" - YouTube", "").trim()
        val playlistTitle = if (rawTitle.isNotBlank()) rawTitle else "YouTube Playlist"
        val coverUrl = doc.select("meta[property=og:image]").attr("content")

        val tracks = mutableListOf<PorterTrack>()

        for (script in doc.select("script")) {
            val html = script.html()
            if (html.contains("ytInitialData")) {
                val jsonStart = html.indexOf("{", html.indexOf("ytInitialData"))
                val jsonEnd = html.lastIndexOf("};") + 1
                if (jsonStart in 0 until jsonEnd) {
                    try {
                        val jsonStr = html.substring(jsonStart, jsonEnd)
                        val regexVideo = Pattern.compile("\"playlistVideoRenderer\":\\s*\\{([\\s\\S]*?)\"navigationEndpoint\"")
                        val videoMatcher = regexVideo.matcher(jsonStr)
                        while (videoMatcher.find()) {
                            val chunk = "{" + videoMatcher.group(1) + "}"
                            val titleMatch = Pattern.compile("\"title\":\\s*\\{\\s*\"runs\":\\s*\\[\\s*\\{\\s*\"text\":\\s*\"(.*?)\"").matcher(chunk)
                            val bylineMatch = Pattern.compile("\"shortBylineText\":\\s*\\{\\s*\"runs\":\\s*\\[\\s*\\{\\s*\"text\":\\s*\"(.*?)\"").matcher(chunk)
                            if (titleMatch.find()) {
                                val t = titleMatch.group(1).orEmpty()
                                val a = if (bylineMatch.find()) bylineMatch.group(1).orEmpty() else "YouTube Artist"
                                if (t.isNotBlank()) {
                                    tracks.add(
                                        PorterTrack(
                                            title = t,
                                            artist = a,
                                            coverUri = coverUrl
                                        )
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        if (tracks.isEmpty()) {
            val rows = doc.select("tr.pl-video, div.yt-lockup-content")
            for (r in rows) {
                val t = r.select(".pl-video-title a, a[title]").text()
                val a = r.select(".pl-video-owner a, .yt-lockup-byline a").text()
                if (t.isNotBlank()) {
                    tracks.add(PorterTrack(title = t, artist = a.ifBlank { "YouTube Artist" }, coverUri = coverUrl))
                }
            }
        }

        return PorterResult(
            playlistTitle = playlistTitle,
            sourcePlatform = "YouTube Music",
            coverUri = coverUrl,
            tracks = tracks
        )
    }

    private fun parseDeezerPlaylist(url: String): PorterResult {
        val matcher = DEEZER_PLAYLIST_PATTERN.matcher(url)
        if (!matcher.find()) {
            throw IllegalArgumentException("Invalid Deezer playlist link.")
        }
        val playlistId = matcher.group(1) ?: throw IllegalArgumentException("Could not extract Deezer playlist ID.")

        val apiUrl = "https://api.deezer.com/playlist/$playlistId"
        val request = Request.Builder().url(apiUrl).build()
        val response = httpClient.newCall(request).execute()
        val body = response.body.string()

        val json = JSONObject(body)
        if (json.has("error")) {
            throw IllegalStateException("Deezer API error: ${json.optJSONObject("error")?.optString("message")}")
        }

        val title = json.optString("title", "Deezer Playlist")
        val cover = json.optString("picture_medium", "")

        val tracksJson = json.optJSONObject("tracks")?.optJSONArray("data")
        val tracks = mutableListOf<PorterTrack>()

        if (tracksJson != null) {
            for (i in 0 until tracksJson.length()) {
                val item = tracksJson.getJSONObject(i)
                val trackTitle = item.optString("title", "")
                val artistName = item.optJSONObject("artist")?.optString("name", "Unknown Artist") ?: "Unknown Artist"
                val albumName = item.optJSONObject("album")?.optString("title", "") ?: ""
                val durationSec = item.optLong("duration", 0L)
                val trackCover = item.optJSONObject("album")?.optString("cover_medium", cover) ?: cover

                if (trackTitle.isNotBlank()) {
                    tracks.add(
                        PorterTrack(
                            title = trackTitle,
                            artist = artistName,
                            album = albumName,
                            durationMs = durationSec * 1000L,
                            coverUri = trackCover
                        )
                    )
                }
            }
        }

        return PorterResult(
            playlistTitle = title,
            sourcePlatform = "Deezer",
            coverUri = cover,
            tracks = tracks
        )
    }

    private fun parseAppleMusicPlaylist(url: String): PorterResult {
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .timeout(15000)
            .get()

        val pageTitle = doc.title().replace(" - Apple Music", "").trim()
        var playlistTitle = pageTitle
        var coverUrl = doc.select("meta[property=og:image]").attr("content")

        val tracks = mutableListOf<PorterTrack>()

        // 1. Try schema.org JSON-LD
        val jsonLdElements = doc.select("script[type=application/ld+json]")
        for (elem in jsonLdElements) {
            try {
                val json = JSONObject(elem.data())
                val name = json.optString("name", "")
                if (name.isNotBlank()) playlistTitle = name

                val trackList = json.optJSONArray("track")
                if (trackList != null) {
                    for (i in 0 until trackList.length()) {
                        val t = trackList.getJSONObject(i)
                        val tName = t.optString("name", "")
                        val byArtist = t.optJSONObject("byArtist")?.optString("name") ?: ""
                        if (tName.isNotBlank()) {
                            tracks.add(
                                PorterTrack(
                                    title = tName,
                                    artist = byArtist.ifBlank { "Unknown Artist" },
                                    coverUri = coverUrl
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Fallback: Parse music:song meta tags
        if (tracks.isEmpty()) {
            val rows = doc.select(".songs-list-row, .track-list-item, div[data-testid=track-row]")
            for (row in rows) {
                val songTitle = row.select(".songs-list-row__song-name, .track-title").text()
                val artistName = row.select(".songs-list-row__link, .track-artist").text()
                if (songTitle.isNotBlank()) {
                    tracks.add(
                        PorterTrack(
                            title = songTitle,
                            artist = artistName.ifBlank { "Unknown Artist" },
                            coverUri = coverUrl
                        )
                    )
                }
            }
        }

        return PorterResult(
            playlistTitle = playlistTitle.ifBlank { "Apple Music Playlist" },
            sourcePlatform = "Apple Music",
            coverUri = coverUrl,
            tracks = tracks
        )
    }
}

package io.github.sekademi.spotufi.data.playlist

import android.content.Context
import android.net.Uri
import io.github.sekademi.spotufi.data.entity.SongsModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

/**
 * Data model for locally created, offline playlists.
 */
data class LocalPlaylist(
    val id: String,
    val title: String,
    val description: String = "",
    val coverUri: String = "",
    val songs: List<SongsModel> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val trackCount: Int get() = songs.size
}

/**
 * Local playlist manager:
 * - Persistent JSON storage in app private files dir
 * - Complete CRUD (create, read, update, delete, reorder)
 * - M3U8 & JSON playlist export & import
 */
object LocalPlaylistManager {

    private const val FILE_NAME = "local_playlists.json"
    private val lock = Any()

    private fun getStorageFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    fun getPlaylists(context: Context): List<LocalPlaylist> = synchronized(lock) {
        val file = getStorageFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val content = file.readText()
            if (content.isBlank()) return emptyList()
            val jsonArray = JSONArray(content)
            val list = mutableListOf<LocalPlaylist>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(parsePlaylist(obj))
            }
            list.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            android.util.Log.e("LocalPlaylistManager", "Failed to load playlists: ${e.message}")
            emptyList()
        }
    }

    fun getPlaylist(context: Context, id: String): LocalPlaylist? {
        return getPlaylists(context).firstOrNull { it.id == id }
    }

    fun createPlaylist(
        context: Context,
        title: String,
        description: String = "",
        initialSong: SongsModel? = null,
    ): LocalPlaylist = synchronized(lock) {
        val id = UUID.randomUUID().toString().take(8)
        val songsList = if (initialSong != null) listOf(initialSong) else emptyList()
        val cover = initialSong?.coverUri ?: ""
        val newPlaylist = LocalPlaylist(
            id = id,
            title = title.trim().ifBlank { "My Playlist" },
            description = description.trim(),
            coverUri = cover,
            songs = songsList,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        val all = getPlaylists(context).toMutableList()
        all.add(0, newPlaylist)
        savePlaylists(context, all)
        newPlaylist
    }

    fun deletePlaylist(context: Context, id: String): Boolean = synchronized(lock) {
        val all = getPlaylists(context).toMutableList()
        val removed = all.removeAll { it.id == id }
        if (removed) {
            savePlaylists(context, all)
        }
        removed
    }

    fun addSongToPlaylist(context: Context, playlistId: String, song: SongsModel): Boolean = synchronized(lock) {
        val all = getPlaylists(context).toMutableList()
        val index = all.indexOfFirst { it.id == playlistId }
        if (index < 0) return false
        val current = all[index]
        if (current.songs.any { it.id == song.id || (it.url.isNotBlank() && it.url == song.url) }) {
            return false // already present
        }
        val updatedSongs = current.songs + song
        val updatedCover = if (current.coverUri.isBlank()) song.coverUri else current.coverUri
        all[index] = current.copy(
            songs = updatedSongs,
            coverUri = updatedCover,
            updatedAt = System.currentTimeMillis(),
        )
        savePlaylists(context, all)
        true
    }

    fun removeSongFromPlaylist(context: Context, playlistId: String, songId: Int): Boolean = synchronized(lock) {
        val all = getPlaylists(context).toMutableList()
        val index = all.indexOfFirst { it.id == playlistId }
        if (index < 0) return false
        val current = all[index]
        val updatedSongs = current.songs.filterNot { it.id == songId }
        val updatedCover = updatedSongs.firstOrNull()?.coverUri ?: ""
        all[index] = current.copy(
            songs = updatedSongs,
            coverUri = updatedCover,
            updatedAt = System.currentTimeMillis(),
        )
        savePlaylists(context, all)
        true
    }

    fun reorderSongs(context: Context, playlistId: String, fromPos: Int, toPos: Int): Boolean = synchronized(lock) {
        val all = getPlaylists(context).toMutableList()
        val index = all.indexOfFirst { it.id == playlistId }
        if (index < 0) return false
        val current = all[index]
        if (fromPos !in current.songs.indices || toPos !in current.songs.indices) return false
        val mutableSongs = current.songs.toMutableList()
        val item = mutableSongs.removeAt(fromPos)
        mutableSongs.add(toPos, item)
        all[index] = current.copy(
            songs = mutableSongs,
            updatedAt = System.currentTimeMillis(),
        )
        savePlaylists(context, all)
        true
    }

    private fun savePlaylists(context: Context, playlists: List<LocalPlaylist>) {
        val file = getStorageFile(context)
        val jsonArray = JSONArray()
        for (pl in playlists) {
            jsonArray.put(serializePlaylist(pl))
        }
        file.writeText(jsonArray.toString(2))
    }

    private fun serializePlaylist(playlist: LocalPlaylist): JSONObject {
        val obj = JSONObject()
        obj.put("id", playlist.id)
        obj.put("title", playlist.title)
        obj.put("description", playlist.description)
        obj.put("coverUri", playlist.coverUri)
        obj.put("createdAt", playlist.createdAt)
        obj.put("updatedAt", playlist.updatedAt)

        val songsArray = JSONArray()
        for (s in playlist.songs) {
            val sObj = JSONObject()
            sObj.put("id", s.id)
            sObj.put("title", s.title)
            sObj.put("singer", s.singer)
            sObj.put("album", s.album)
            sObj.put("coverUri", s.coverUri)
            sObj.put("url", s.url)
            sObj.put("spotifyTrackId", s.spotifyTrackId)
            sObj.put("explicit", s.explicit)
            sObj.put("durationMs", s.durationMs)
            sObj.put("artistIds", s.artistIds)
            songsArray.put(sObj)
        }
        obj.put("songs", songsArray)
        return obj
    }

    private fun parsePlaylist(obj: JSONObject): LocalPlaylist {
        val id = obj.optString("id", "")
        val title = obj.optString("title", "Untitled")
        val description = obj.optString("description", "")
        val coverUri = obj.optString("coverUri", "")
        val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
        val updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())

        val songsList = mutableListOf<SongsModel>()
        val songsArray = obj.optJSONArray("songs")
        if (songsArray != null) {
            for (i in 0 until songsArray.length()) {
                val sObj = songsArray.getJSONObject(i)
                songsList.add(
                    SongsModel(
                        id = sObj.optInt("id", 0),
                        title = sObj.optString("title", ""),
                        album = sObj.optString("album", ""),
                        singer = sObj.optString("singer", ""),
                        coverUri = sObj.optString("coverUri", ""),
                        url = sObj.optString("url", ""),
                        spotifyTrackId = sObj.optString("spotifyTrackId", ""),
                        explicit = sObj.optBoolean("explicit", false),
                        durationMs = sObj.optInt("durationMs", 0),
                        artistIds = sObj.optString("artistIds", ""),
                    )
                )
            }
        }

        return LocalPlaylist(
            id = id,
            title = title,
            description = description,
            coverUri = coverUri,
            songs = songsList,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    /**
     * Export a local playlist to M3U8 standard playlist format.
     */
    fun exportToM3u8(playlist: LocalPlaylist, outputStream: OutputStream): Boolean {
        return try {
            outputStream.bufferedWriter().use { writer ->
                writer.write("#EXTM3U\n")
                writer.write("#PLAYLIST:${playlist.title}\n")
                for (song in playlist.songs) {
                    val durationSec = (song.durationMs / 1000).coerceAtLeast(0)
                    writer.write("#EXTINF:$durationSec,${song.singer} - ${song.title}\n")
                    writer.write("${song.url}\n")
                }
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("LocalPlaylistManager", "Failed to export M3U8: ${e.message}")
            false
        }
    }

    /**
     * Import a playlist from an M3U8 file.
     */
    fun importFromM3u8(context: Context, inputStream: InputStream, defaultTitle: String = "Imported Playlist"): LocalPlaylist? {
        return try {
            val reader = BufferedReader(InputStreamReader(inputStream))
            var title = defaultTitle
            val songs = mutableListOf<SongsModel>()
            var currentTitle = ""
            var currentArtist = ""
            var currentDurationSec = 0

            var line = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#PLAYLIST:")) {
                    title = trimmed.removePrefix("#PLAYLIST:").trim().ifBlank { defaultTitle }
                } else if (trimmed.startsWith("#EXTINF:")) {
                    val info = trimmed.removePrefix("#EXTINF:")
                    val parts = info.split(",", limit = 2)
                    currentDurationSec = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
                    val artistTitle = parts.getOrNull(1)?.trim().orEmpty()
                    if (artistTitle.contains(" - ")) {
                        val at = artistTitle.split(" - ", limit = 2)
                        currentArtist = at[0].trim()
                        currentTitle = at[1].trim()
                    } else {
                        currentTitle = artistTitle
                        currentArtist = ""
                    }
                } else if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                    // This is the URL / identifier
                    val songUrl = trimmed
                    val id = songUrl.hashCode()
                    songs.add(
                        SongsModel(
                            id = id,
                            title = currentTitle.ifBlank { "Track ${songs.size + 1}" },
                            album = title,
                            singer = currentArtist,
                            coverUri = "",
                            url = songUrl,
                            durationMs = currentDurationSec * 1000,
                        )
                    )
                    currentTitle = ""
                    currentArtist = ""
                    currentDurationSec = 0
                }
                line = reader.readLine()
            }

            if (songs.isEmpty()) return null

            val created = createPlaylist(context, title, "Imported from M3U8")
            val all = getPlaylists(context).toMutableList()
            val idx = all.indexOfFirst { it.id == created.id }
            if (idx >= 0) {
                all[idx] = all[idx].copy(songs = songs)
                savePlaylists(context, all)
            }
            all.firstOrNull { it.id == created.id }
        } catch (e: Exception) {
            android.util.Log.e("LocalPlaylistManager", "Failed to import M3U8: ${e.message}")
            null
        }
    }
}

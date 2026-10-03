package io.github.sekademi.spotufi.data.preferences

import android.content.Context
import io.github.sekademi.spotufi.data.entity.SongsModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val LIKED_FILE_NAME = "local_liked_songs.json"
private val likedLock = Any()

fun addLikedSong(context: Context, song: SongsModel) {
    addLikedSongId(context, song.id.toString())
    synchronized(likedLock) {
        val current = getLocalLikedSongs(context).toMutableList()
        current.removeAll { it.id == song.id || (it.url.isNotBlank() && it.url == song.url) }
        current.add(0, song)
        saveLocalLikedSongs(context, current)
    }
}

fun removeLikedSong(context: Context, songId: String) {
    removeLikedSongId(context, songId)
    val intId = songId.toIntOrNull()
    synchronized(likedLock) {
        val current = getLocalLikedSongs(context).toMutableList()
        current.removeAll { it.id.toString() == songId || (intId != null && it.id == intId) }
        saveLocalLikedSongs(context, current)
    }
}

fun getLocalLikedSongs(context: Context): List<SongsModel> = synchronized(likedLock) {
    val file = File(context.filesDir, LIKED_FILE_NAME)
    if (!file.exists()) return emptyList()
    return try {
        val text = file.readText()
        if (text.isBlank()) return emptyList()
        val jsonArray = JSONArray(text)
        val list = mutableListOf<SongsModel>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                SongsModel(
                    id = obj.optInt("id", -1),
                    title = obj.optString("title", ""),
                    album = obj.optString("album", ""),
                    singer = obj.optString("singer", ""),
                    coverUri = obj.optString("coverUri", ""),
                    url = obj.optString("url", ""),
                    spotifyTrackId = obj.optString("spotifyTrackId", ""),
                    explicit = obj.optBoolean("explicit", false),
                    durationMs = obj.optInt("durationMs", 0),
                    artistIds = obj.optString("artistIds", ""),
                )
            )
        }
        list
    } catch (e: Exception) {
        android.util.Log.e("LikedSongPref", "Failed to read local liked songs: ${e.message}")
        emptyList()
    }
}

private fun saveLocalLikedSongs(context: Context, songs: List<SongsModel>) {
    try {
        val jsonArray = JSONArray()
        for (song in songs) {
            val obj = JSONObject().apply {
                put("id", song.id)
                put("title", song.title)
                put("album", song.album)
                put("singer", song.singer)
                put("coverUri", song.coverUri)
                put("url", song.url)
                put("spotifyTrackId", song.spotifyTrackId)
                put("explicit", song.explicit)
                put("durationMs", song.durationMs)
                put("artistIds", song.artistIds)
            }
            jsonArray.put(obj)
        }
        val file = File(context.filesDir, LIKED_FILE_NAME)
        file.writeText(jsonArray.toString(2))
    } catch (e: Exception) {
        android.util.Log.e("LikedSongPref", "Failed to save local liked songs: ${e.message}")
    }
}

fun addLikedSongId(context: Context, songId: String) {
    val sharedPreferences = context.getSharedPreferences("LikedSongs", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    editor.putString(songId, songId)
    editor.apply()
}

fun removeLikedSongId(context: Context, songId: String) {
    val sharedPreferences = context.getSharedPreferences("LikedSongs", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    editor.remove(songId)
    editor.apply()
}

fun isSongLiked(context: Context, songId: String): Boolean {
    val sharedPreferences = context.getSharedPreferences("LikedSongs", Context.MODE_PRIVATE)
    return sharedPreferences.contains(songId)
}

fun getLikedSongIds(context: Context): Set<Int> {
    val sharedPreferences = context.getSharedPreferences("LikedSongs", Context.MODE_PRIVATE)
    return sharedPreferences.all.keys.mapNotNull { it.toIntOrNull() }.toSet()
}

fun getSongsByIds(songIds: Set<Int>, songs: List<SongsModel>): List<SongsModel> {
    return songs.filter { song -> song.id in songIds }
}
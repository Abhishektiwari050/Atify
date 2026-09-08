package io.github.sekademi.spotufi.data.preferences

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import io.github.sekademi.spotufi.data.entity.SongsModel
import org.json.JSONObject
import java.io.File

/**
 * Tracks downloaded (offline) tracks. Each entry is keyed by the song id and stores
 * the full [SongsModel] plus the local file path the audio was saved to, so the
 * Downloads screen can list them offline and [io.github.sekademi.spotufi.di.SongPlayer]
 * can play the local file instead of re-resolving + streaming from YouTube.
 *
 * On Android 10+ (API 29), MediaStore returns `content://` URIs instead of
 * filesystem paths. All existence checks, deletions, and date lookups must
 * handle both URI schemes correctly.
 */
private const val PREF = "Downloads"
private const val KEY_DL_SORT = "download_sort_option"
private const val KEY_DL_DESC = "download_sort_desc"

enum class DownloadSortOption(val label: String) {
    DATE("Added"),
    TITLE("Title"),
    ARTIST("Artist")
}

fun getDownloadsSortOption(context: Context): DownloadSortOption {
    val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    val saved = prefs.getString(KEY_DL_SORT, DownloadSortOption.DATE.name)
    return runCatching { DownloadSortOption.valueOf(saved!!) }.getOrDefault(DownloadSortOption.DATE)
}

fun isDownloadsSortDescending(context: Context): Boolean {
    return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .getBoolean(KEY_DL_DESC, true) // Default true for DATE (recent)
}

fun setDownloadsSortOption(context: Context, option: DownloadSortOption, desc: Boolean) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
        .putString(KEY_DL_SORT, option.name)
        .putBoolean(KEY_DL_DESC, desc)
        .apply()
}

// ── Content URI helpers ─────────────────────────────────────────────────

/** Whether [path] is a MediaStore content URI (vs. a filesystem path). */
private fun isContentUri(path: String): Boolean = path.startsWith("content://")

/**
 * Check whether a downloaded file still exists, supporting both content:// URIs
 * and regular filesystem paths.
 */
private fun fileExists(context: Context, path: String): Boolean {
    if (path.isBlank()) return false
    if (isContentUri(path)) {
        return runCatching {
            context.contentResolver.openFileDescriptor(Uri.parse(path), "r")?.use { true }
        }.getOrNull() == true
    }
    return File(path).exists()
}

/**
 * Delete a downloaded file, supporting both content:// URIs and filesystem paths.
 */
private fun deleteFile(context: Context, path: String) {
    if (path.isBlank()) return
    if (isContentUri(path)) {
        runCatching { context.contentResolver.delete(Uri.parse(path), null, null) }
    } else {
        runCatching { File(path).delete() }
    }
}

/**
 * Get the last-modified / date-added timestamp for a downloaded file, supporting
 * both content:// URIs (via MediaStore DATE_ADDED) and filesystem paths.
 */
private fun fileTimestamp(context: Context, path: String): Long {
    if (path.isBlank()) return 0L
    if (isContentUri(path)) {
        return runCatching {
            context.contentResolver.query(
                Uri.parse(path),
                arrayOf(MediaStore.Audio.Media.DATE_ADDED),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    // DATE_ADDED is in seconds since epoch; convert to millis
                    cursor.getLong(0) * 1000L
                } else 0L
            } ?: 0L
        }.getOrDefault(0L)
    }
    return File(path).lastModified()
}

// ── JSON serialization ──────────────────────────────────────────────────

private fun SongsModel.toJson(filePath: String): String = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("album", album)
    put("singer", singer)
    put("coverUri", coverUri)
    put("url", url)
    put("spotifyTrackId", spotifyTrackId)
    put("explicit", explicit)
    put("durationMs", durationMs)
    put("artistIds", artistIds)
    put("filePath", filePath)
}.toString()

private fun parse(json: String): Pair<SongsModel, String>? = runCatching {
    val o = JSONObject(json)
    SongsModel(
        id = o.getInt("id"),
        title = o.getString("title"),
        album = o.optString("album"),
        singer = o.getString("singer"),
        coverUri = o.optString("coverUri"),
        url = o.getString("url"),
        spotifyTrackId = o.optString("spotifyTrackId"),
        explicit = o.optBoolean("explicit", false),
        durationMs = o.optInt("durationMs", 0),
        artistIds = o.optString("artistIds"),
    ) to o.optString("filePath")
}.getOrNull()

// ── Public API ──────────────────────────────────────────────────────────

fun addDownload(context: Context, song: SongsModel, filePath: String) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
        .putString(song.id.toString(), song.toJson(filePath))
        .apply()
}

fun removeDownload(context: Context, songId: String) {
    val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    prefs.getString(songId, null)?.let { json ->
        parse(json)?.second?.let { path -> deleteFile(context, path) }
    }
    prefs.edit().remove(songId).apply()
}

fun isDownloaded(context: Context, songId: String): Boolean {
    val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    val json = prefs.getString(songId, null) ?: return false
    val path = parse(json)?.second ?: return false
    if (!fileExists(context, path)) {
        prefs.edit().remove(songId).apply()
        return false
    }
    return true
}

fun getDownloadedSongs(context: Context): List<SongsModel> {
    val entries = getDownloadedEntries(context)
    val sortOption = getDownloadsSortOption(context)
    val descending = isDownloadsSortDescending(context)
    return when (sortOption) {
        DownloadSortOption.TITLE -> if (descending) entries.map { it.first }.sortedByDescending { it.title.lowercase() } else entries.map { it.first }.sortedBy { it.title.lowercase() }
        DownloadSortOption.ARTIST -> if (descending) entries.map { it.first }.sortedByDescending { it.singer.lowercase() } else entries.map { it.first }.sortedBy { it.singer.lowercase() }
        DownloadSortOption.DATE -> if (descending) entries.sortedByDescending { (_, path) -> fileTimestamp(context, path) }.map { it.first } else entries.sortedBy { (_, path) -> fileTimestamp(context, path) }.map { it.first }
    }
}

/** Local file path for a track query (SongsModel.url), if downloaded and present on disk. */
fun downloadedPathForQuery(context: Context, query: String): String? {
    val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    for (v in prefs.all.values) {
        val (song, path) = (v as? String)?.let(::parse) ?: continue
        if (song.url == query && path.isNotBlank() && fileExists(context, path)) return path
    }
    return null
}

/** Every downloaded track paired with its on-disk file path. */
fun getDownloadedEntries(context: Context): List<Pair<SongsModel, String>> =
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .all.values.mapNotNull { (it as? String)?.let(::parse) }

/** Delete every downloaded file and forget all download entries. Returns count removed. */
fun clearAllDownloads(context: Context): Int {
    val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    val entries = getDownloadedEntries(context)
    entries.forEach { (_, path) -> if (path.isNotBlank()) deleteFile(context, path) }
    val currentSort = getDownloadsSortOption(context)
    val currentDesc = isDownloadsSortDescending(context)
    prefs.edit().clear().apply()
    setDownloadsSortOption(context, currentSort, currentDesc)
    return entries.size
}



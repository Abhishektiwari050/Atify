package io.github.sekademi.spotufi.data.preferences

import android.content.Context
import io.github.sekademi.spotufi.di.StreamResolver
import java.io.File

object StorageManager {

    data class CacheBreakdown(
        val audioBytes: Long,
        val imageBytes: Long,
        val lyricsBytes: Long,
        val totalBytes: Long,
    )

    private fun getFolderSizeBytes(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            val files = current.listFiles() ?: continue
            for (file in files) {
                if (file.isDirectory) {
                    stack.add(file)
                } else {
                    size += file.length()
                }
            }
        }
        return size
    }

    private fun deleteDirContents(dir: File?) {
        if (dir == null || !dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                deleteDirContents(file)
                file.delete()
            } else {
                file.delete()
            }
        }
    }

    fun getCacheBreakdown(context: Context): CacheBreakdown {
        val audio = StreamResolver.getMediaCacheSizeBytes(context)

        // Images are stored in cacheDir/image_cache or Coil cache
        val imageCacheDir = File(context.cacheDir, "image_cache")
        val imageBytes = getFolderSizeBytes(imageCacheDir)

        val lyricsBytes = getLyricsCacheSizeBytes(context)

        val total = audio + imageBytes + lyricsBytes
        return CacheBreakdown(
            audioBytes = audio,
            imageBytes = imageBytes,
            lyricsBytes = lyricsBytes,
            totalBytes = total,
        )
    }

    fun clearAllCaches(context: Context): CacheBreakdown {
        // 1. Audio stream cache
        StreamResolver.clearMediaCache(context)

        // 2. Coil image cache
        val imageCacheDir = File(context.cacheDir, "image_cache")
        deleteDirContents(imageCacheDir)

        // 3. Lyrics cache
        clearLyricsCache(context)

        return getCacheBreakdown(context)
    }
}

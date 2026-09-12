package io.github.sekademi.spotufi.di

import android.content.Context
import androidx.collection.LruCache
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.target
import coil3.size.Size
import coil3.toBitmap

/**
 * Singleton color-extraction helper backed by an in-memory LRU cache.
 *
 * Each Coil request requests a 64×64 thumbnail via `.size(64, 64)`, reducing
 * per-decode heap from ~4 MB (full-res software bitmap) down to ~16 KB — a
 * 99.6% reduction. Results are cached so repeated recompositions (e.g. from
 * the 300ms progress ticker in MiniPlayer/PlayerScreen) never re-issue a
 * network or disk read.
 */
object PaletteExtractor {

    /**
     * Two separate LRU caches, one per extraction path.
     * 128 entries covers a large listening session without exceeding ~2 MB.
     */
    private val darkVibrantCache = LruCache<String, Color>(128)
    private val mutedCache = LruCache<String, Color>(128)

    /**
     * Extract the dark-vibrant swatch (used for MiniPlayer backgrounds).
     * Calls [onColorExtracted] synchronously from cache when already resolved,
     * otherwise dispatches one async Coil request and calls back on completion.
     */
    fun extractFirstColorFromImageUrl(
        context: Context,
        imageUrl: String,
        onColorExtracted: (Color) -> Unit,
    ) {
        if (imageUrl.isBlank()) return

        darkVibrantCache[imageUrl]?.let { cached ->
            onColorExtracted(cached)
            return
        }

        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            // 64×64 is sufficient for palette analysis (~16 KB instead of ~4 MB)
            .size(Size(64, 64))
            .allowHardware(false)
            .target(
                onSuccess = { result ->
                    val bitmap = result.toBitmap()
                    Palette.from(bitmap).generate { palette ->
                        val rgb = palette?.darkVibrantSwatch?.rgb
                        if (rgb != null) {
                            val color = Color(rgb or (0xFF shl 24))
                            darkVibrantCache.put(imageUrl, color)
                            onColorExtracted(color)
                        }
                    }
                    bitmap.recycle()
                },
            )
            .build()
        context.imageLoader.enqueue(request)
    }

    /**
     * Extract the muted swatch (used for PlayerScreen / AlbumScreen gradients).
     */
    fun extractSecondColorFromCoverUrl(
        context: Context,
        imageUrl: String,
        onColorExtracted: (Color) -> Unit,
    ) {
        if (imageUrl.isBlank()) return

        mutedCache[imageUrl]?.let { cached ->
            onColorExtracted(cached)
            return
        }

        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(Size(64, 64))
            .allowHardware(false)
            .target(
                onSuccess = { result ->
                    val bitmap = result.toBitmap()
                    Palette.from(bitmap).generate { palette ->
                        val rgb = palette?.mutedSwatch?.rgb
                        if (rgb != null) {
                            val color = Color(rgb or (0xFF shl 24))
                            mutedCache.put(imageUrl, color)
                            onColorExtracted(color)
                        }
                    }
                    bitmap.recycle()
                },
            )
            .build()
        context.imageLoader.enqueue(request)
    }
}

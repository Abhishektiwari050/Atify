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
                    runCatching {
                        val bitmap = result.toBitmap()
                        Palette.from(bitmap).generate { palette ->
                            val rgb = palette?.darkVibrantSwatch?.rgb
                            if (rgb != null) {
                                val color = Color(rgb or (0xFF shl 24))
                                darkVibrantCache.put(imageUrl, color)
                                onColorExtracted(color)
                            }
                        }
                    }
                },
            )
            .build()
        context.imageLoader.enqueue(request)
    }

    /**
     * Extract the vibrant/dominant cover color (used for PlayerScreen / AlbumScreen / PlaylistScreen gradients).
     * Prioritizes vibrantSwatch and dominantSwatch over mutedSwatch, so screens accurately reflect
     * the artwork's authentic color palette rather than a muddy desaturated tone.
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
                    runCatching {
                        val bitmap = result.toBitmap()
                        Palette.from(bitmap).generate { palette ->
                            val swatch = palette?.vibrantSwatch
                                ?: palette?.dominantSwatch
                                ?: palette?.darkVibrantSwatch
                                ?: palette?.lightVibrantSwatch
                                ?: palette?.mutedSwatch
                            val rgb = swatch?.rgb
                            if (rgb != null) {
                                val baseColor = Color(rgb or (0xFF shl 24))
                                val tunedColor = adjustForScreenBackground(baseColor)
                                mutedCache.put(imageUrl, tunedColor)
                                onColorExtracted(tunedColor)
                            }
                        }
                    }
                },
            )
            .build()
        context.imageLoader.enqueue(request)
    }

    private fun adjustForScreenBackground(color: Color): Color {
        val lum = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
        return when {
            lum > 0.60f -> {
                // If the cover color is excessively bright (e.g. bright yellow or light pastel),
                // scale it down so white text on the top bar and player remains 100% readable.
                val factor = 0.55f / lum
                Color(color.red * factor, color.green * factor, color.blue * factor, 1f)
            }
            lum < 0.05f -> {
                // If it's near-black, give it a subtle lift so the cover color is perceptible.
                Color(
                    red = (color.red + 0.12f).coerceAtMost(1f),
                    green = (color.green + 0.12f).coerceAtMost(1f),
                    blue = (color.blue + 0.12f).coerceAtMost(1f),
                    alpha = 1f
                )
            }
            else -> color
        }
    }
}

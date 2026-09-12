package io.github.sekademi.spotufi.ui.screens.player

import android.graphics.Color as AndroidColor
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Plays a Spotify Canvas clip: a short, muted, looping video filling the
 * now-playing background. Uses a dedicated ExoPlayer (separate from the audio
 * engine) released when the composable leaves.
 *
 * A minimal [DefaultLoadControl] is configured: the canvas clip is typically
 * 3–8 seconds, so a 3-second max buffer (vs. the default 50 MB) keeps RAM
 * consumption negligible. Proper disposal prevents [PlayerView] → ExoPlayer
 * surface attachment leaks on recomposition.
 */
@OptIn(UnstableApi::class)
@Composable
fun CanvasVideo(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val exo = remember(url) {
        // Minimal buffer profile: canvas clips loop continuously so we only
        // need ~3 s of look-ahead, not the default 50 s / 50 MB budget.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs        = */ 1_000,
                /* maxBufferMs        = */ 3_000,
                /* bufferForPlaybackMs           = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000,
            )
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(url) {
        onDispose {
            // Detach from any PlayerView before releasing to avoid surface
            // attachment errors when the Composable is removed during navigation.
            exo.stop()
            exo.clearMediaItems()
            exo.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exo
                useController = false
                controllerAutoShow = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setArtworkDisplayMode(PlayerView.ARTWORK_DISPLAY_MODE_OFF)
                setDefaultArtwork(null)
                hideController()
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setBackgroundColor(AndroidColor.TRANSPARENT)
            }
        },
        update = { playerView ->
            // Re-attach after recomposition (e.g. on config change) so the
            // surface is never pointing at a different or released player.
            if (playerView.player !== exo) playerView.player = exo
        },
    )
}

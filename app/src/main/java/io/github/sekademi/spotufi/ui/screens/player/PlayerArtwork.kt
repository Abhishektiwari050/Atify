package io.github.sekademi.spotufi.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.sekademi.spotufi.data.entity.SongsModel

/**
 * Responsive artwork carousel for the now-playing screen.
 * Supports swiping through queue tracks with smooth snap-scrolling,
 * and hides artwork behind an alpha scrim when a Spotify Canvas video is active.
 */
@Composable
fun PlayerArtwork(
    queueSongs: List<SongsModel>,
    currentCoverUri: String,
    pagerState: PagerState,
    canvasUrl: String?,
    showStaticCover: Boolean = false,
    onToggleCover: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val artworkAlpha = if (canvasUrl != null && !showStaticCover) 0f else 1f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleCover,
            )
    ) {
        if (queueSongs.isEmpty()) {
            AsyncImage(
                modifier = Modifier
                    .sizeIn(maxWidth = 385.dp, maxHeight = 385.dp)
                    .aspectRatio(1f)
                    .padding(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .alpha(artworkAlpha),
                model = currentCoverUri,
                contentScale = ContentScale.Crop,
                contentDescription = "Cover Art"
            )
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .sizeIn(maxWidth = 385.dp, maxHeight = 385.dp)
                    .aspectRatio(1f),
            ) { page ->
                AsyncImage(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .alpha(artworkAlpha),
                    model = queueSongs.getOrNull(page)?.coverUri ?: currentCoverUri,
                    contentScale = ContentScale.Crop,
                    contentDescription = "Cover Art"
                )
            }
        }
    }
}

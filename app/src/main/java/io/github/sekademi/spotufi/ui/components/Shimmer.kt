package io.github.sekademi.spotufi.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.shimmer(
    visible: Boolean = true,
    shape: Shape = RoundedCornerShape(4.dp),
    baseColor: Color = Color(0xFF222226),
    highlightColor: Color = Color(0xFF383840),
    durationMillis: Int = 1200,
): Modifier = composed {
    if (!visible) return@composed this

    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    val translateAnim = transition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ShimmerTranslate",
    )

    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateAnim.value - 300f, translateAnim.value - 300f),
        end = Offset(translateAnim.value, translateAnim.value),
    )

    this
        .clip(shape)
        .background(brush)
}

@Composable
fun TrackItemShimmer(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .shimmer(shape = RoundedCornerShape(6.dp)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(14.dp)
                    .shimmer(shape = RoundedCornerShape(4.dp)),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(11.dp)
                    .shimmer(shape = RoundedCornerShape(4.dp)),
            )
        }
    }
}

@Composable
fun TrackListShimmer(count: Int = 8, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(count) {
            TrackItemShimmer()
        }
    }
}

@Composable
fun GridItemShimmer(size: Dp = 150.dp, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .shimmer(shape = RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(width = size * 0.8f, height = 14.dp)
                .shimmer(shape = RoundedCornerShape(4.dp)),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(width = size * 0.5f, height = 11.dp)
                .shimmer(shape = RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
fun QuickPickItemShimmer(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF222228)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .shimmer(shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(13.dp)
                .padding(end = 12.dp)
                .shimmer(shape = RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
fun HomeCarouselShimmer(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .size(width = 170.dp, height = 20.dp)
                .shimmer(shape = RoundedCornerShape(4.dp)),
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(4) {
                GridItemShimmer(size = 140.dp)
            }
        }
    }
}

/**
 * Commercial-grade Spotify-style home feed skeleton shimmer.
 * Pre-renders the greeting row, 6-card shortcuts grid, and horizontal carousels,
 * preventing sudden layout shifts while feed endpoints resolve.
 */
@Composable
fun HomeFeedShimmer(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F13))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // Header row skeleton
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 180.dp, height = 26.dp)
                    .shimmer(shape = RoundedCornerShape(6.dp)),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .shimmer(shape = CircleShape),
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .shimmer(shape = CircleShape),
                )
            }
        }

        // 6-item Quick-Picks grid skeleton
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickPickItemShimmer(modifier = Modifier.weight(1f))
                    QuickPickItemShimmer(modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Section Carousels
        HomeCarouselShimmer()
        HomeCarouselShimmer()

        Spacer(modifier = Modifier.height(130.dp))
    }
}

/**
 * 2-column grid shimmer for Category/Browse playlists and search categories.
 */
@Composable
fun CategoryGridShimmer(count: Int = 8, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 130.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(count) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shimmer(shape = RoundedCornerShape(8.dp)),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(14.dp)
                        .shimmer(shape = RoundedCornerShape(4.dp)),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(11.dp)
                        .shimmer(shape = RoundedCornerShape(4.dp)),
                )
            }
        }
    }
}
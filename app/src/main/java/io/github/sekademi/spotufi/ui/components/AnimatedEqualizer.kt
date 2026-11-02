package io.github.sekademi.spotufi.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Animated 3-bar audio equalizer indicator matching Spotify and Apple Music now-playing visuals.
 * Bounces harmonically when [isPlaying] is true, and settles into a subtle resting state when paused.
 */
@Composable
fun AnimatedEqualizer(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    color: Color = Color(0xFF1ED760),
    size: Dp = 16.dp,
    barCount: Int = 3,
) {
    val transition = rememberInfiniteTransition(label = "EqualizerTransition")

    val bar1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.98f,
        animationSpec = infiniteRepeatable(
            animation = tween(430, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "EqualizerBar1",
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(560, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "EqualizerBar2",
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(490, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "EqualizerBar3",
    )

    Canvas(modifier = modifier.size(size)) {
        val width = this.size.width
        val height = this.size.height
        val spacing = width * 0.16f
        val totalSpacing = (barCount - 1) * spacing
        val barWidth = (width - totalSpacing) / barCount
        val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)

        val fractions = if (isPlaying) {
            floatArrayOf(bar1, bar2, bar3)
        } else {
            floatArrayOf(0.35f, 0.65f, 0.40f)
        }

        for (i in 0 until barCount) {
            val frac = fractions[i % fractions.size]
            val barH = (height * frac).coerceAtLeast(barWidth)
            val x = i * (barWidth + spacing)
            val y = height - barH

            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = cornerRadius,
            )
        }
    }
}

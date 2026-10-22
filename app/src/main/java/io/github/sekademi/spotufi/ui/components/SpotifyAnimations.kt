package io.github.sekademi.spotufi.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sekademi.spotufi.R
import io.github.sekademi.spotufi.di.RepeatMode
import io.github.sekademi.spotufi.ui.theme.AppPalette

/**
 * Tactile bouncing container that compresses on touch press and springs back on release,
 * matching Spotify's interactive control physics.
 */
@Composable
fun BouncyScaleBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressedScale: Float = 0.91f,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "bouncyScale",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                },
            ),
    ) {
        content()
    }
}

/**
 * Spotify-style interactive Like/Add button with elastic bounce scale animation
 * and smooth color transitions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimatedHeartButton(
    isLiked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    size: Dp = 26.dp,
    likedColor: Color = Color(0xFF1ED760),
    unlikedColor: Color = Color.White,
    useHeartIcon: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }

    LaunchedEffect(isLiked) {
        if (isLiked) {
            scale.animateTo(1.36f, tween(110, easing = FastOutSlowInEasing))
            scale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        } else {
            scale.animateTo(0.84f, tween(90))
            scale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
    }

    val animatedTint by animateColorAsState(
        targetValue = if (isLiked) likedColor else unlikedColor,
        animationSpec = tween(220),
        label = "heartTint",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                },
                onLongClick = onLongClick?.let { action ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        action()
                    }
                },
            ),
    ) {
        val painter = if (useHeartIcon) {
            if (isLiked) painterResource(R.drawable.ic_heart_filled) else painterResource(R.drawable.ic_heart_outline)
        } else {
            if (isLiked) painterResource(R.drawable.added) else painterResource(R.drawable.ic_add)
        }

        Icon(
            modifier = Modifier.size(size),
            painter = painter,
            tint = animatedTint,
            contentDescription = if (isLiked) "Liked" else "Like",
        )
    }
}

/**
 * Animated Spotify-style shuffle toggle with spring overshoot on press.
 */
@Composable
fun AnimatedShuffleButton(
    shuffle: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    val haptic = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }

    val animatedTint by animateColorAsState(
        targetValue = if (shuffle) Color(AppPalette.toArgb()) else Color.White,
        animationSpec = tween(200),
        label = "shuffleTint",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onToggle()
                },
            ),
    ) {
        LaunchedEffect(shuffle) {
            scale.animateTo(1.25f, tween(90, easing = FastOutSlowInEasing))
            scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        }

        Icon(
            modifier = Modifier.size(size),
            tint = animatedTint,
            painter = painterResource(id = R.drawable.ic_player_shuffle),
            contentDescription = "Shuffle",
        )
    }
}

/**
 * Animated Spotify-style repeat toggle with tri-state badge transitions and spring bounce.
 */
@Composable
fun AnimatedRepeatButton(
    repeat: RepeatMode,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    val haptic = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }

    val animatedTint by animateColorAsState(
        targetValue = if (repeat != RepeatMode.OFF) Color(0xFF1ED760) else Color.White,
        animationSpec = tween(200),
        label = "repeatTint",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onToggle()
                },
            ),
    ) {
        LaunchedEffect(repeat) {
            scale.animateTo(1.25f, tween(90, easing = FastOutSlowInEasing))
            scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    modifier = Modifier.size(size),
                    tint = animatedTint,
                    painter = painterResource(id = R.drawable.ic_repeat),
                    contentDescription = "Repeat",
                )
                if (repeat == RepeatMode.ONE) {
                    Text(
                        text = "1",
                        color = Color.Black,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(x = 0.dp, y = (-1).dp),
                    )
                }
            }
        }
    }
}

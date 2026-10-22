package io.github.sekademi.spotufi.ui.screens.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sekademi.spotufi.di.SongPlayer
import io.github.sekademi.spotufi.ui.viewmodel.PlayerViewModel

/**
 * Material 3 styled custom slider with smooth touch scrubbing, animated track height expansion,
 * and circular thumb indicator on interaction.
 */
@Composable
fun CustomSlider(
    modifier: Modifier = Modifier,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    var isDragging by remember { mutableStateOf(false) }

    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 5.5.dp else 3.5.dp,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
        ),
        label = "trackHeight"
    )
    val thumbRadius by animateDpAsState(
        targetValue = if (isDragging) 7.5.dp else 4.5.dp,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
        ),
        label = "thumbRadius"
    )

    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
        .coerceIn(0f, 1f)

    val density = LocalDensity.current

    Box(
        modifier = modifier
            .height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val mapped = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                    onValueChange(mapped)
                    onValueChangeFinished?.invoke()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished?.invoke()
                    },
                    onDragCancel = {
                        isDragging = false
                        onValueChangeFinished?.invoke()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val newFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val mapped = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                        onValueChange(mapped)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val trackHeightPx = with(density) { trackHeight.toPx() }
        val thumbRadiusPx = with(density) { thumbRadius.toPx() }

        Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
            val trackY = size.height / 2f
            val thumbX = (fraction * size.width).coerceIn(thumbRadiusPx, size.width - thumbRadiusPx)

            // Background inactive track
            drawLine(
                color = Color(0x38FFFFFF),
                start = Offset(0f, trackY),
                end = Offset(size.width, trackY),
                strokeWidth = trackHeightPx,
                cap = StrokeCap.Round
            )
            // Active played track
            drawLine(
                color = Color.White,
                start = Offset(0f, trackY),
                end = Offset(thumbX, trackY),
                strokeWidth = trackHeightPx,
                cap = StrokeCap.Round
            )
            // Always-visible tactile thumb indicator
            drawCircle(
                color = Color.White,
                radius = thumbRadiusPx,
                center = Offset(thumbX, trackY)
            )
        }
    }
}

/**
 * Player progress bar and timestamp labels with touch-scrubbing.
 */
@Composable
fun PlayerProgress(
    songDurationText: String,
    songProgressText: String,
    songPlayingState: Boolean,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    Column(modifier = modifier) {
        CustomSlider(
            value = if (isDragging) dragValue else SongPlayer.getDuration().toFloat().let { dur ->
                if (dur > 0f) (SongPlayer.getCurrentPosition().toFloat() / dur).coerceIn(0f, 1f) else 0f
            },
            onValueChange = { newValue ->
                isDragging = true
                dragValue = newValue
            },
            onValueChangeFinished = {
                val seekDur = SongPlayer.getDuration()
                if (seekDur > 0) SongPlayer.seekTo((dragValue * seekDur).toLong())
                isDragging = false
                if (!songPlayingState) {
                    SongPlayer.play()
                    playerViewModel.updateSongState(
                        playerViewModel.currentSongCoverUri.value,
                        playerViewModel.currentSongTitle.value,
                        playerViewModel.currentSongSinger.value,
                        true,
                        playerViewModel.currentSongId.value,
                        playerViewModel.currentSongIndex.value,
                        playerViewModel.currentSongAlbum.value
                    )
                }
            },
            valueRange = 0f..1f,
            steps = 0,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 20.dp, 16.dp, 0.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(25.dp, 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isDragging) {
                    val dur = SongPlayer.getDuration()
                    if (dur > 0) playerViewModel.formatDuration((dragValue * dur).toLong()) else "0:00"
                } else songProgressText,
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = songDurationText,
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

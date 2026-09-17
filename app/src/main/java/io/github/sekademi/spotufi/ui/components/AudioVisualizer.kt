package io.github.sekademi.spotufi.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.sekademi.spotufi.data.preferences.getVisualizerStyle
import io.github.sekademi.spotufi.data.preferences.isVisualizerEnabled
import io.github.sekademi.spotufi.data.preferences.setVisualizerStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 60fps beat-reactive audio spectrum visualizer.
 * Renders dynamic frequency bars, smooth Bezier waves, or floating glow peaks
 * that oscillate organically with playback state, audio energy, and tempo.
 */
@Composable
fun AudioVisualizer(
    isPlaying: Boolean,
    primaryColor: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 28,
) {
    val context = LocalContext.current
    val enabled = remember(context) { isVisualizerEnabled(context) }
    if (!enabled) return

    var currentStyle by remember { mutableIntStateOf(getVisualizerStyle(context)) }

    // Phase animation driving the organic waveform oscillation
    val phaseAnim = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            phaseAnim.animateTo(
                targetValue = 1000f * 2 * PI.toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 200000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            )
        } else {
            phaseAnim.stop()
        }
    }

    // Dynamic bar heights with peak decay
    val barAmplitudes = remember { FloatArray(barCount) { 0.08f } }
    val peakAmplitudes = remember { FloatArray(barCount) { 0.08f } }

    LaunchedEffect(isPlaying) {
        var tick = 0
        while (isActive) {
            val phase = phaseAnim.value
            for (i in 0 until barCount) {
                if (isPlaying) {
                    val normalizedIndex = i.toFloat() / barCount
                    // Combine bass resonance (lower frequencies have higher amplitude) with mid-treble shimmering
                    val bassEnvelope = 1f - (normalizedIndex * 0.45f)
                    val wave1 = sin(phase * 1.5f + i * 0.45f)
                    val wave2 = sin(phase * 3.2f - i * 0.3f)
                    val wave3 = sin(phase * 0.8f + i * 0.85f)
                    val combined = abs(wave1 * 0.45f + wave2 * 0.35f + wave3 * 0.20f)
                    val target = (combined * bassEnvelope * 0.85f + 0.12f).coerceIn(0.08f, 0.98f)

                    // Smooth attack, organic decay
                    barAmplitudes[i] += (target - barAmplitudes[i]) * 0.45f
                    if (barAmplitudes[i] > peakAmplitudes[i]) {
                        peakAmplitudes[i] = barAmplitudes[i]
                    } else {
                        peakAmplitudes[i] = (peakAmplitudes[i] - 0.02f).coerceAtLeast(barAmplitudes[i])
                    }
                } else {
                    // Decay smoothly to idle state when paused
                    barAmplitudes[i] = (barAmplitudes[i] - 0.04f).coerceAtLeast(0.04f)
                    peakAmplitudes[i] = (peakAmplitudes[i] - 0.04f).coerceAtLeast(0.04f)
                }
            }
            tick++
            delay(16L) // ~60 fps
        }
    }

    val gradientColors = remember(primaryColor) {
        listOf(
            primaryColor.copy(alpha = 0.95f),
            Color(0xFF3DABFF),
            Color(0xFFFFC862),
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 24.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                currentStyle = (currentStyle + 1) % 3
                setVisualizerStyle(context, currentStyle)
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val brush = Brush.horizontalGradient(gradientColors, startX = 0f, endX = canvasWidth)

            when (currentStyle) {
                0 -> {
                    // Style 0: Dancing Spectrum Bars with Peak Dots
                    val totalSpacing = canvasWidth * 0.30f
                    val barWidth = (canvasWidth - totalSpacing) / barCount
                    val spacing = totalSpacing / (barCount - 1).coerceAtLeast(1)

                    for (i in 0 until barCount) {
                        val x = i * (barWidth + spacing)
                        val amp = barAmplitudes[i]
                        val barHeight = (canvasHeight * amp).coerceAtLeast(3.dp.toPx())
                        val top = canvasHeight - barHeight

                        // Main rounded bar
                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(x, top),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                        )

                        // Floating peak dot
                        val peakTop = canvasHeight - (canvasHeight * peakAmplitudes[i]) - 3.dp.toPx()
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.85f),
                            radius = (barWidth / 2.2f).coerceAtMost(3.dp.toPx()),
                            center = Offset(x + barWidth / 2f, peakTop.coerceAtLeast(2.dp.toPx())),
                        )
                    }
                }
                1 -> {
                    // Style 1: Smooth Fluid Bezier Waveform
                    val path = Path()
                    path.moveTo(0f, canvasHeight)

                    val stepX = canvasWidth / (barCount - 1)
                    val points = (0 until barCount).map { i ->
                        val amp = barAmplitudes[i]
                        val y = canvasHeight - (canvasHeight * amp * 0.95f)
                        Offset(i * stepX, y)
                    }

                    path.lineTo(points.first().x, points.first().y)
                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val midX = (p0.x + p1.x) / 2f
                        val midY = (p0.y + p1.y) / 2f
                        path.quadraticTo(p0.x, p0.y, midX, midY)
                    }
                    path.lineTo(points.last().x, points.last().y)
                    path.lineTo(canvasWidth, canvasHeight)
                    path.close()

                    // Subtle filled glowing wave
                    drawPath(
                        path = path,
                        brush = Brush.verticalGradient(
                            listOf(primaryColor.copy(alpha = 0.35f), Color.Transparent),
                            startY = 0f,
                            endY = canvasHeight,
                        ),
                    )

                    // Sharp vibrant outline
                    val strokePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val midX = (p0.x + p1.x) / 2f
                            val midY = (p0.y + p1.y) / 2f
                            quadraticTo(p0.x, p0.y, midX, midY)
                        }
                        lineTo(points.last().x, points.last().y)
                    }
                    drawPath(
                        path = strokePath,
                        brush = brush,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                2 -> {
                    // Style 2: Floating Glow Peaks with mirror reflection
                    val totalSpacing = canvasWidth * 0.25f
                    val colWidth = (canvasWidth - totalSpacing) / barCount
                    val spacing = totalSpacing / (barCount - 1).coerceAtLeast(1)
                    val centerY = canvasHeight / 2f

                    for (i in 0 until barCount) {
                        val x = i * (colWidth + spacing) + colWidth / 2f
                        val amp = barAmplitudes[i]
                        val halfH = (canvasHeight * 0.45f * amp).coerceAtLeast(2.dp.toPx())

                        drawLine(
                            brush = brush,
                            start = Offset(x, centerY - halfH),
                            end = Offset(x, centerY + halfH),
                            strokeWidth = colWidth.coerceAtMost(4.dp.toPx()),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}

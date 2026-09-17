package io.github.sekademi.spotufi.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sekademi.spotufi.audio.PitchDetector
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Real-time vocal pitch guide meter for Sing-Along Karaoke:
 * - Shows current sung note (e.g. C4, G#3)
 * - Tuning cents needle (-50 cents Flat to +50 cents Sharp)
 * - Accuracy rating / score tracking
 * - Mic permission launcher integration
 */
@Composable
fun PitchGuideMeter(
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var hasMicPermission by remember { mutableStateOf(PitchDetector.hasPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            PitchDetector.start(context)
        }
    }

    DisposableEffect(Unit) {
        if (hasMicPermission) {
            PitchDetector.start(context)
        }
        onDispose {
            PitchDetector.stop()
        }
    }

    val pitchResult by PitchDetector.pitchState.collectAsState()

    // Accuracy scoring tracker
    var totalFrames by remember { mutableIntStateOf(0) }
    var inTuneFrames by remember { mutableIntStateOf(0) }

    if (pitchResult.confidence > 0.6f && pitchResult.frequencyHz > 0f) {
        totalFrames++
        if (abs(pitchResult.centsOffset) <= 15f) {
            inTuneFrames++
        }
    }

    val score = if (totalFrames > 15) {
        ((inTuneFrames.toFloat() / totalFrames) * 100f).roundToInt().coerceIn(0, 100)
    } else 100

    val centsAnimated by animateFloatAsState(
        targetValue = pitchResult.centsOffset.coerceIn(-50f, 50f),
        animationSpec = tween(durationMillis = 80),
        label = "centsNeedle",
    )

    val tuningColor by animateColorAsState(
        targetValue = when {
            pitchResult.frequencyHz <= 0f -> Color.Gray
            abs(centsAnimated) <= 12f -> Color(0xFF1ED760) // Perfect In-Tune green
            abs(centsAnimated) <= 25f -> Color(0xFFFFD54F) // Slightly off
            else -> Color(0xFFFF5252)                       // Sharp or Flat red
        },
        label = "tuningColor",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF1E1E26), Color(0xFF14141A))
                ),
                shape = RoundedCornerShape(16.dp),
            )
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        if (!hasMicPermission) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Microphone Access Required", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Grant mic permission to detect your singing pitch in real time.", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1ED760), RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Enable Mic", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        } else {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(tuningColor.copy(alpha = 0.2f), CircleShape)
                                .border(1.5.dp, tuningColor, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (pitchResult.frequencyHz > 0f) pitchResult.noteName else "--",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            val statusText = when {
                                pitchResult.frequencyHz <= 0f -> "Sing to match pitch…"
                                abs(centsAnimated) <= 12f -> "In Tune! 🎯"
                                centsAnimated < -12f -> "Flat ♭ (${centsAnimated.roundToInt()}¢)"
                                else -> "Sharp ♯ (+${centsAnimated.roundToInt()}¢)"
                            }
                            Text(
                                text = statusText,
                                color = tuningColor,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                            )
                            Text(
                                text = if (pitchResult.frequencyHz > 0f) "${pitchResult.frequencyHz.roundToInt()} Hz" else "Listening…",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("PITCH ACCURACY", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "$score%",
                                color = if (score >= 80) Color(0xFF1ED760) else Color(0xFFFFD54F),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "✕",
                            color = Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onClose() }
                                .padding(4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Pitch Tuning Bar / Needle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    // Center zero mark
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(2.dp)
                            .height(6.dp)
                            .background(Color(0xFF1ED760))
                    )

                    // Needle
                    val fraction = ((centsAnimated + 50f) / 100f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth(fraction)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(10.dp)
                                .background(tuningColor, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

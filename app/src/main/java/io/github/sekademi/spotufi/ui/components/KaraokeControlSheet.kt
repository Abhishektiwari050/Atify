package io.github.sekademi.spotufi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sekademi.spotufi.R
import io.github.sekademi.spotufi.di.SongPlayer

/**
 * Apple Music Sing style Karaoke / Vocal Remover controller.
 * Controls real-time center-channel vocal reduction via [SongPlayer.setVocalAttenuation].
 */
@Composable
fun KaraokeControlSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = modifier,
    ) {
        val attenuation by SongPlayer.vocalAttenuation.collectAsState()
        val percentage = (attenuation * 100f).toInt()
        val isActive = attenuation > 0.01f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF2A2A2A),
                            Color(0xFF181818),
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isActive) Color(0xFF1ED760).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_microphone),
                                contentDescription = "Karaoke",
                                tint = if (isActive) Color(0xFF1ED760) else Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Sing-Along Karaoke",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (isActive) "Vocal reduction: $percentage%" else "Original vocals (Off)",
                                color = if (isActive) Color(0xFF1ED760) else Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                            )
                        }
                    }

                    Text(
                        text = "Done",
                        color = Color(0xFF1ED760),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onDismiss() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Slider(
                    value = attenuation,
                    onValueChange = { SongPlayer.setVocalAttenuation(it) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF1ED760),
                        activeTrackColor = Color(0xFF1ED760),
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Quick presets
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    KaraokePresetPill(
                        label = "Off",
                        selected = attenuation < 0.05f,
                        onClick = { SongPlayer.setVocalAttenuation(0f) },
                    )
                    KaraokePresetPill(
                        label = "Guide 50%",
                        selected = attenuation in 0.45f..0.55f,
                        onClick = { SongPlayer.setVocalAttenuation(0.50f) },
                    )
                    KaraokePresetPill(
                        label = "Sing 85%",
                        selected = attenuation in 0.80f..0.88f,
                        onClick = { SongPlayer.setVocalAttenuation(0.85f) },
                    )
                    KaraokePresetPill(
                        label = "Solo 100%",
                        selected = attenuation > 0.95f,
                        onClick = { SongPlayer.setVocalAttenuation(1.0f) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KaraokePresetPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF1ED760) else Color.White.copy(alpha = 0.12f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

package io.github.sekademi.spotufi.ui.screens.player

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sekademi.spotufi.R
import io.github.sekademi.spotufi.data.api.SpotifySync
import io.github.sekademi.spotufi.data.preferences.addLikedSongId
import io.github.sekademi.spotufi.data.preferences.isSongLiked
import io.github.sekademi.spotufi.data.preferences.removeLikedSongId
import io.github.sekademi.spotufi.ui.theme.AppPalette
import kotlinx.coroutines.delay

@Composable
fun PlayerInfo(
    songTitle: String,
    songSinger: String,
    songId: Int,
    context: Context,
    isLiked: MutableState<Boolean>,
    source: String = "",
    quality: String = "",
    isResolving: Boolean = false,
    resolveStatus: String = "",
    resolveError: String? = null,
    onArtistClick: (() -> Unit)? = null,
    spotifyTrackId: String = "",
    onShowSavedIn: (() -> Unit)? = null,
    onQualityClick: (() -> Unit)? = null,
) {
    var snackbarMessage by remember { mutableStateOf("") }
    var snackbarVisible by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(snackbarVisible) {
        if (snackbarVisible) {
            delay(1500)
            snackbarVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp, 0.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Column {
                    Text(
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        text = songTitle,
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = songSinger,
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier
                            .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000)
                            .then(
                                if (onArtistClick != null) Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onArtistClick() } else Modifier
                            ),
                    )
                    val isResolvingState = isResolving && resolveStatus.isNotBlank()
                    val hasError = !resolveError.isNullOrBlank()
                    val displaySource = if (isResolvingState) "Resolving" else if (hasError) "Error" else source
                    if (displaySource.isNotBlank()) {
                        var showDetails by remember(songId) { mutableStateOf(false) }
                        val isLossless = source.startsWith("Lossless")
                        val badgeColor = when {
                            hasError -> Color(0xFFFF334B)
                            isResolvingState -> Color(0xFFFFB300)
                            isLossless -> Color(0xFFE5B842)
                            source == "Spotify" -> Color(0xFF1ED760)
                            source == "Downloaded" -> Color(0xFF00E5FF)
                            else -> Color(0xFF7986CB)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeColor.copy(alpha = 0.12f))
                                .border(width = 0.75.dp, color = badgeColor.copy(alpha = 0.35f), shape = RoundedCornerShape(6.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    showDetails = !showDetails
                                    onQualityClick?.invoke()
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.5.dp)
                                    .clip(CircleShape)
                                    .background(badgeColor)
                            )
                            Text(
                                text = when {
                                    hasError -> resolveError
                                    isResolvingState -> if (showDetails) resolveStatus else "CONNECTING"
                                    showDetails -> when {
                                        isLossless -> "LOSSLESS FLAC" + (if (quality.isNotBlank()) " • $quality" else "")
                                        source == "Spotify" -> "SPOTIFY 320k" + (if (quality.isNotBlank()) " • $quality" else "")
                                        source == "Downloaded" -> "OFFLINE FLAC" + (if (quality.isNotBlank()) " • $quality" else "")
                                        else -> "STREAM OPUS" + (if (quality.isNotBlank()) " • $quality" else "")
                                    }
                                    isLossless -> "LOSSLESS FLAC"
                                    source == "Spotify" -> "SPOTIFY 320k"
                                    source == "Downloaded" -> "OFFLINE"
                                    else -> "HQ AUDIO"
                                },
                                color = badgeColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }

            Icon(
                modifier = Modifier
                    .size(26.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        if (isLiked.value && onShowSavedIn != null) {
                            onShowSavedIn()
                            return@clickable
                        }
                        if (isLiked.value) {
                            removeLikedSongId(context, songId.toString())
                            snackbarMessage = "Removed from Liked Songs"
                        } else {
                            addLikedSongId(context, songId.toString())
                            snackbarMessage = "Added to Liked Songs"
                        }
                        snackbarVisible = true
                        isLiked.value = isSongLiked(context, songId.toString())
                        SpotifySync.setTrackSaved(context, spotifyTrackId, isLiked.value)
                    },
                painter = if (isLiked.value) {
                    painterResource(id = R.drawable.added)
                } else {
                    painterResource(id = R.drawable.ic_add)
                },
                tint = if (isLiked.value) {
                    Color(AppPalette.toArgb())
                } else {
                    Color.White
                },
                contentDescription = if (isLiked.value) "Liked" else "Like"
            )
        }

        AnimatedVisibility(
            visible = snackbarVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2E2E2E))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = snackbarMessage,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

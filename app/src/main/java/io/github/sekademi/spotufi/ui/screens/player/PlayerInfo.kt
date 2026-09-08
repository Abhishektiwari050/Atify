package io.github.sekademi.spotufi.ui.screens.player

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
) {
    var snackbarMessage by remember { mutableStateOf("") }
    var snackbarVisible by remember { mutableStateOf(false) }

    LaunchedEffect(snackbarVisible) {
        if (snackbarVisible) {
            delay(1500)
            snackbarVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(25.dp, 10.dp)
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
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onArtistClick != null) Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onArtistClick() } else Modifier,
                    )
                    val isResolvingState = isResolving && resolveStatus.isNotBlank()
                    val hasError = !resolveError.isNullOrBlank()
                    val displaySource = if (isResolvingState) "Resolving" else if (hasError) "Error" else source
                    if (displaySource.isNotBlank()) {
                        val badgeColor = when {
                            hasError -> Color(0xFFFF6B6B)
                            isResolvingState -> Color(0xFF3DABFF)
                            source == "Spotify" -> Color(0xFF1ED760)
                            source.startsWith("Lossless") -> Color(0xFFFFC862)
                            source == "Downloaded" -> Color(0xFF9C9C9C)
                            else -> Color(0xFFFF6B6B)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 3.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(badgeColor)
                            )
                            Text(
                                text = when {
                                    hasError -> resolveError
                                    isResolvingState -> resolveStatus
                                    else -> {
                                        (if (source == "YouTube") "Streamed" else source) +
                                            (if (quality.isNotBlank()) " • $quality" else "")
                                    }
                                },
                                color = badgeColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 5.dp),
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

package io.github.sekademi.spotufi.ui.screens

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import io.github.sekademi.spotufi.R
import io.github.sekademi.spotufi.data.preferences.addLikedSongId
import io.github.sekademi.spotufi.data.preferences.isSongLiked
import io.github.sekademi.spotufi.data.preferences.removeLikedSongId
import io.github.sekademi.spotufi.di.SongPlayer
import io.github.sekademi.spotufi.ui.viewmodel.sharedPlayerViewModel
import kotlinx.coroutines.delay
import java.util.Date

/**
 * Dedicated Car Mode screen:
 * - High-contrast, minimal distraction dashboard
 * - Oversized 72dp+ touch targets designed for driving safety
 * - Full-screen swipe gestures (swipe left for Next, swipe right for Previous)
 * - Keeps screen awake via FLAG_KEEP_SCREEN_ON
 * - Real-time clock display
 */
@Composable
fun CarModeScreen(navController: NavController) {
    val context = LocalContext.current
    val view = LocalView.current
    val playerViewModel = sharedPlayerViewModel()

    val currentTitle by playerViewModel.currentSongTitle
    val currentSinger by playerViewModel.currentSongSinger
    val currentCover by playerViewModel.currentSongCoverUri
    val currentId by playerViewModel.currentSongId
    val isPlaying by playerViewModel.currentSongPlayingState
    val queueSongs by playerViewModel.queue

    // Keep screen on while in Car Mode
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }

    // Digital clock updater (updates every 5 seconds)
    var currentTimeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val format = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
            currentTimeString = DateFormat.format(format, Date()).toString()
            delay(5000L)
        }
    }

    var isLiked by remember(currentId) {
        mutableStateOf(isSongLiked(context, currentId.toString()))
    }

    // Swipe gesture accumulator
    var totalDragX by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0E))
            .statusBarsPadding()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { totalDragX = 0f },
                    onDragEnd = {
                        if (totalDragX < -150f) {
                            // Swipe Left -> Next
                            playerViewModel.playNextSongs(queueSongs, context)
                        } else if (totalDragX > 150f) {
                            // Swipe Right -> Previous
                            playerViewModel.playPreviousSong(queueSongs, context)
                        }
                        totalDragX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        totalDragX += dragAmount
                    }
                )
            }
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Exit button & Live Clock
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { navController.popBackStack() }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Exit Car Mode",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exit", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "CAR MODE",
                        color = Color(0xFF1ED760),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    Text(
                        text = currentTimeString,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Center: Oversized Album Artwork & Song Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF1A1A24))
                ) {
                    if (currentCover.isNotBlank()) {
                        AsyncImage(
                            model = currentCover,
                            contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_home_filled),
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier
                                .size(80.dp)
                                .align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = currentTitle.ifBlank { "Not Playing" },
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = currentSinger.ifBlank { "Atify Audio" },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Bottom: Oversized Driving Controls (Hit targets 72dp+)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Like button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (isLiked) {
                                    removeLikedSongId(context, currentId.toString())
                                    isLiked = false
                                } else {
                                    addLikedSongId(context, currentId.toString())
                                    isLiked = true
                                }
                            }
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                            ),
                            contentDescription = "Like",
                            tint = if (isLiked) Color(0xFF1ED760) else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Previous Track (Large)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                playerViewModel.playPreviousSong(queueSongs, context)
                            }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_player_back),
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Play / Pause (Giant 84dp Touch Target)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1ED760))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (SongPlayer.isPlaying()) {
                                    SongPlayer.pause()
                                } else {
                                    SongPlayer.play()
                                }
                            }
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isPlaying) R.drawable.ic_paused else R.drawable.ic_playing
                            ),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    // Next Track (Large)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                playerViewModel.playNextSongs(queueSongs, context)
                            }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_player_skip),
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Queue button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                navController.navigate(io.github.sekademi.spotufi.ui.navigation.Routes.Queue.route)
                            }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_view_list),
                            contentDescription = "Queue",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Swipe left/right anywhere to skip tracks",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

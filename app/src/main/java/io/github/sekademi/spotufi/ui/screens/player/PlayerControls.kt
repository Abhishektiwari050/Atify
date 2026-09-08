package io.github.sekademi.spotufi.ui.screens.player

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.ui.compose.material3.buttons.PlayPauseButton
import androidx.navigation.NavController
import io.github.sekademi.spotufi.R
import io.github.sekademi.spotufi.data.entity.SongsModel
import io.github.sekademi.spotufi.data.preferences.isSongLiked
import io.github.sekademi.spotufi.di.RepeatMode
import io.github.sekademi.spotufi.di.SongPlayer
import io.github.sekademi.spotufi.ui.navigation.Routes
import io.github.sekademi.spotufi.ui.theme.AppPalette
import io.github.sekademi.spotufi.ui.viewmodel.PlayerViewModel

/**
 * Playback controls row: shuffle, previous, play/pause/buffering, next, tri-state repeat.
 */
@Composable
fun PlayerFull(
    songPlayingState: Boolean,
    playerViewModel: PlayerViewModel,
    context: Context,
    isLiked: MutableState<Boolean>,
    shuffle: Boolean,
    repeat: RepeatMode,
    queueSongs: List<SongsModel>,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Icon(
            modifier = Modifier
                .size(25.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    playerViewModel.updateShuffleState(!shuffle)
                },
            tint = if (shuffle) Color(AppPalette.toArgb()) else Color.White,
            painter = painterResource(id = R.drawable.ic_player_shuffle),
            contentDescription = "Shuffle"
        )

        Icon(
            modifier = Modifier
                .size(35.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    playerViewModel.playPreviousSong(queueSongs, context)
                    isLiked.value = isSongLiked(context, playerViewModel.currentSongId.value.toString())
                },
            tint = Color.White,
            painter = painterResource(id = R.drawable.ic_player_back),
            contentDescription = "Previous"
        )

        val isLocatingOrBuffering = playerViewModel.isResolving.value || playerViewModel.isBuffering.value
        if (isLocatingOrBuffering) {
            Box(
                modifier = Modifier
                    .requiredSize(64.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    color = Color.Black,
                    strokeWidth = 3.dp,
                )
            }
        } else {
            PlayPauseButton(
                player = SongPlayer.exoPlayer,
                modifier = Modifier
                    .requiredSize(64.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = Color.Black,
                ),
            )
        }

        Icon(
            modifier = Modifier
                .size(35.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    playerViewModel.playNextSongs(queueSongs, context)
                    isLiked.value = isSongLiked(context, playerViewModel.currentSongId.value.toString())
                },
            tint = Color.White,
            painter = painterResource(id = R.drawable.ic_player_skip),
            contentDescription = "Next"
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .size(width = 32.dp, height = 40.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    val nextRepeat = when (repeat) {
                        RepeatMode.OFF -> RepeatMode.ALL
                        RepeatMode.ALL -> RepeatMode.ONE
                        RepeatMode.ONE -> RepeatMode.OFF
                    }
                    playerViewModel.updateRepeatState(nextRepeat)
                }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    tint = if (repeat != RepeatMode.OFF) Color(0xFF1ED760) else Color.White,
                    painter = painterResource(id = R.drawable.ic_repeat),
                    contentDescription = "Repeat"
                )
                if (repeat == RepeatMode.ONE) {
                    Text(
                        text = "1",
                        color = Color(0xFF1ED760),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(y = (-1).dp)
                    )
                }
            }
            if (repeat != RepeatMode.OFF) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(Color(0xFF1ED760), shape = CircleShape)
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

/**
 * Current audio route (Bluetooth / Headphones / This device), Share button, and Queue access.
 */
@Composable
fun PlayerConnectRow(
    navController: NavController,
    context: Context,
    currentTrack: SongsModel?,
) {
    val routeName = remember(currentTrack?.id) { currentAudioRoute(context) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 25.dp, vertical = 4.dp),
    ) {
        // Device / Spotify Connect indicator (green).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_devices),
                tint = Color(0xFF1ED760),
                modifier = Modifier.size(18.dp),
                contentDescription = "Device",
            )
            Text(
                text = routeName,
                color = Color(0xFF1ED760),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .widthIn(max = 170.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_share),
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        val link = currentTrack?.spotifyTrackId
                            ?.takeIf { it.isNotBlank() }
                            ?.let { "https://open.spotify.com/track/$it" }
                            ?: "${currentTrack?.title ?: ""} ${currentTrack?.singer ?: ""}".trim()
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, link)
                        }
                        context.startActivity(Intent.createChooser(send, "Share"))
                    },
                contentDescription = "Share",
            )
            Spacer(modifier = Modifier.width(22.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                tint = Color.White,
                modifier = Modifier
                    .size(23.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { navController.navigate(Routes.Queue.route) },
                contentDescription = "Queue",
            )
        }
    }
}

@Composable
fun PlayerEndInfo() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Icon(
            modifier = Modifier.size(22.dp),
            painter = painterResource(id = R.drawable.ic_devices),
            tint = Color.White,
            contentDescription = "Devices"
        )
        Icon(
            modifier = Modifier.size(16.dp),
            painter = painterResource(id = R.drawable.ic_share),
            tint = Color.White,
            contentDescription = "Share"
        )
    }
}

/**
 * Returns audio output route name (Bluetooth name, Headphones, or This device).
 */
fun currentAudioRoute(context: Context): String {
    return try {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bt = outs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
        if (bt != null) return bt.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth"
        val wired = outs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        if (wired != null) "Headphones" else "This device"
    } catch (e: Exception) {
        "This device"
    }
}

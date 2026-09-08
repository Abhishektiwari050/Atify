package io.github.sekademi.spotufi.ui.screens.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import io.github.sekademi.spotufi.R
import io.github.sekademi.spotufi.data.api.SpotifySync
import io.github.sekademi.spotufi.data.preferences.AlternativeStream
import io.github.sekademi.spotufi.data.preferences.addLikedSongId
import io.github.sekademi.spotufi.data.preferences.alternativeStreamKey
import io.github.sekademi.spotufi.data.preferences.clearAlternativeStream
import io.github.sekademi.spotufi.data.preferences.getAlternativeStream
import io.github.sekademi.spotufi.data.preferences.isDownloaded
import io.github.sekademi.spotufi.data.preferences.isSongLiked
import io.github.sekademi.spotufi.data.preferences.removeDownload
import io.github.sekademi.spotufi.data.preferences.removeLikedSongId
import io.github.sekademi.spotufi.data.preferences.setLocalAlternativeStream
import io.github.sekademi.spotufi.data.preferences.setYouTubeAlternativeStream
import io.github.sekademi.spotufi.di.SongPlayer
import io.github.sekademi.spotufi.ui.components.SavedInSheet
import io.github.sekademi.spotufi.ui.navigation.Routes
import io.github.sekademi.spotufi.ui.navigation.albumRoute
import io.github.sekademi.spotufi.ui.theme.AppPalette
import io.github.sekademi.spotufi.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerOptionsSheet(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    context: Context,
    isLiked: MutableState<Boolean>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSleep by remember { mutableStateOf(false) }
    var showSavedIn by remember { mutableStateOf(false) }
    var showAlternativeStream by remember { mutableStateOf(false) }

    val title = playerViewModel.currentSongTitle.value
    val singer = playerViewModel.currentSongSinger.value
    val cover = playerViewModel.currentSongCoverUri.value
    val album = playerViewModel.currentSongAlbum.value
    val songId = playerViewModel.currentSongId.value
    val currentSong = playerViewModel.queue.value.firstOrNull { it.id == songId }
    var downloaded by remember(songId) { mutableStateOf(isDownloaded(context, songId.toString())) }
    var downloadingNow by remember(songId) { mutableStateOf(currentSong != null && SongPlayer.isDownloading(currentSong.url)) }
    val alternativeKey = currentSong?.let { alternativeStreamKey(it) }.orEmpty()
    var currentAlternative by remember(songId, alternativeKey) {
        mutableStateOf(alternativeKey.takeIf { it.isNotBlank() }?.let { getAlternativeStream(context, it) })
    }
    val localFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val song = currentSong ?: return@rememberLauncherForActivityResult
        val picked = uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                picked,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        setLocalAlternativeStream(context, alternativeKey, picked, picked.lastPathSegment.orEmpty())
        SongPlayer.invalidateResolvedStream(song.url)
        currentAlternative = getAlternativeStream(context, alternativeKey)
        Toast.makeText(context, "Alternative stream set to local file", Toast.LENGTH_SHORT).show()
    }

    if (showSavedIn && currentSong != null) {
        SavedInSheet(
            song = currentSong,
            context = context,
            onDismiss = { showSavedIn = false; onDismiss() },
            onLikedChanged = { isLiked.value = it },
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A1A),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            if (showAlternativeStream) {
                AlternativeStreamEditor(
                    currentAlternative = currentAlternative,
                    enabled = currentSong != null,
                    onBack = { showAlternativeStream = false },
                    onUseYouTube = { text ->
                        val song = currentSong ?: return@AlternativeStreamEditor
                        val videoId = SongPlayer.videoIdFromYouTubeLink(text)
                        if (videoId == null) {
                            Toast.makeText(context, "Paste a YouTube video link or video ID", Toast.LENGTH_SHORT).show()
                        } else {
                            setYouTubeAlternativeStream(context, alternativeKey, videoId)
                            SongPlayer.invalidateResolvedStream(song.url)
                            currentAlternative = getAlternativeStream(context, alternativeKey)
                            Toast.makeText(context, "Alternative stream set to YouTube", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onPickLocal = {
                        localFileLauncher.launch(arrayOf("audio/*"))
                    },
                    onClear = {
                        val song = currentSong ?: return@AlternativeStreamEditor
                        clearAlternativeStream(context, alternativeKey)
                        SongPlayer.invalidateResolvedStream(song.url)
                        currentAlternative = null
                        Toast.makeText(context, "Alternative stream cleared", Toast.LENGTH_SHORT).show()
                    },
                )
            } else if (!showSleep) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    AsyncImage(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        model = cover,
                        contentScale = ContentScale.Crop,
                        contentDescription = "Track thumbnail"
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(singer, color = Color.Gray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider(color = Color(0xFF2A2A2A))

                PlayerMenuRow(
                    icon = Icons.Default.Share,
                    label = "Share"
                ) {
                    val shareText = currentSong?.spotifyTrackId?.takeIf { it.isNotBlank() }
                        ?.let { "https://open.spotify.com/track/$it" }
                        ?: "Listening to $title by $singer"
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(send, "Share"))
                    onDismiss()
                }
                PlayerMenuRow(
                    icon = if (downloaded) Icons.Default.CheckCircle else ImageVector.vectorResource(R.drawable.ic_download),
                    iconTint = if (downloaded) Color(AppPalette.toArgb()) else Color.White,
                    label = when {
                        downloaded -> "Downloaded — remove"
                        downloadingNow -> "Downloading…"
                        else -> "Download"
                    },
                    enabled = currentSong != null && !downloadingNow,
                ) {
                    val song = currentSong ?: return@PlayerMenuRow
                    if (downloaded) {
                        removeDownload(context, song.id.toString())
                        downloaded = false
                    } else {
                        downloadingNow = true
                        SongPlayer.downloadSong(song, context) { ok ->
                            downloadingNow = false
                            downloaded = ok
                        }
                    }
                }
                PlayerMenuRow(
                    icon = Icons.Default.PlayArrow,
                    iconTint = if (currentAlternative != null) Color(AppPalette.toArgb()) else Color.White,
                    label = if (currentAlternative == null) "Alternative stream" else "Alternative stream set",
                    enabled = currentSong != null,
                    trailingArrow = true,
                ) {
                    showAlternativeStream = true
                }
                PlayerMenuRow(
                    icon = if (isLiked.value) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    iconTint = if (isLiked.value) Color(AppPalette.toArgb()) else Color.White,
                    label = if (isLiked.value) "Remove from Liked Songs" else "Add to Liked Songs"
                ) {
                    if (isLiked.value) {
                        removeLikedSongId(context, songId.toString())
                    } else {
                        addLikedSongId(context, songId.toString())
                    }
                    isLiked.value = isSongLiked(context, songId.toString())
                    SpotifySync.setTrackSaved(
                        context, currentSong?.spotifyTrackId.orEmpty(), isLiked.value)
                    onDismiss()
                }
                PlayerMenuRow(
                    icon = Icons.Default.Add,
                    label = "Add to playlist",
                    enabled = currentSong != null,
                    trailingArrow = true,
                ) {
                    showSavedIn = true
                }
                PlayerMenuRow(
                    icon = Icons.AutoMirrored.Filled.List,
                    label = "View queue"
                ) {
                    onDismiss()
                    navController.navigate(Routes.Queue.route)
                }
                val realAlbum = currentSong?.album?.ifBlank { null } ?: album
                PlayerMenuRow(
                    icon = Icons.Default.PlayArrow,
                    label = "Go to album",
                    enabled = realAlbum.isNotBlank()
                ) {
                    onDismiss()
                    navController.navigate(albumRoute(realAlbum, singer))
                }
                PlayerMenuRow(
                    icon = Icons.Default.Person,
                    label = "Go to artist",
                    enabled = singer.isNotBlank()
                ) {
                    onDismiss()
                    playerViewModel.goToArtist(currentSong?.spotifyTrackId.orEmpty(), singer) { route ->
                        navController.navigate(route)
                    }
                }
                PlayerMenuRow(
                    icon = Icons.Default.Notifications,
                    label = "Sleep timer",
                    trailingArrow = true
                ) { showSleep = true }
            } else {
                Text(
                    "Sleep timer",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
                HorizontalDivider(color = Color(0xFF2A2A2A))
                val options = listOf(
                    "Off" to 0L,
                    "5 minutes" to 5L,
                    "15 minutes" to 15L,
                    "30 minutes" to 30L,
                    "45 minutes" to 45L,
                    "1 hour" to 60L
                )
                options.forEach { (label, minutes) ->
                    PlayerMenuRow(icon = Icons.Default.Notifications, label = label) {
                        SongPlayer.setSleepTimer(minutes * 60_000L)
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
fun AlternativeStreamEditor(
    currentAlternative: AlternativeStream?,
    enabled: Boolean,
    onBack: () -> Unit,
    onUseYouTube: (String) -> Unit,
    onPickLocal: () -> Unit,
    onClear: () -> Unit,
) {
    var youtubeText by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onBack() },
                contentDescription = null,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Alternative stream",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = when {
                currentAlternative == null -> "No alternative stream set"
                currentAlternative.isYouTube -> "Current: YouTube video ${currentAlternative.value}"
                currentAlternative.isLocal -> "Current: local file ${currentAlternative.label.ifBlank { currentAlternative.value }}"
                else -> "Current alternative stream"
            },
            color = if (currentAlternative == null) Color(0xFFB3B3B3) else Color(AppPalette.toArgb()),
            fontSize = 13.sp,
            maxLines = 2,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = youtubeText,
            onValueChange = { youtubeText = it },
            enabled = enabled,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
            label = { Text("YouTube link or video ID", color = Color(0xFFB3B3B3)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(enabled = enabled && youtubeText.isNotBlank(), onClick = { onUseYouTube(youtubeText) }) {
                Text("Use YouTube", color = if (enabled && youtubeText.isNotBlank()) AppPalette else Color.Gray)
            }
        }
        PlayerMenuRow(
            icon = Icons.Default.Add,
            label = "Use local audio file",
            enabled = enabled,
        ) {
            onPickLocal()
        }
        PlayerMenuRow(
            icon = Icons.Default.CheckCircle,
            label = "Clear alternative stream",
            enabled = enabled && currentAlternative != null,
            iconTint = Color(0xFFE57373),
        ) {
            onClear()
        }
    }
}

@Composable
fun PlayerMenuRow(
    icon: ImageVector,
    label: String,
    iconTint: Color = Color.White,
    enabled: Boolean = true,
    trailingArrow: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = icon,
            tint = if (enabled) iconTint else Color.Gray,
            modifier = Modifier.size(22.dp),
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = label,
            color = if (enabled) Color.White else Color.Gray,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        if (trailingArrow) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp),
                contentDescription = null
            )
        }
    }
}

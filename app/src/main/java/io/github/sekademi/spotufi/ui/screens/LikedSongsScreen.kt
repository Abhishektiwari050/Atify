package io.github.sekademi.spotufi.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import io.github.sekademi.spotufi.R
import io.github.sekademi.spotufi.data.api.Response
import io.github.sekademi.spotufi.di.SongPlayer
import io.github.sekademi.spotufi.ui.components.Loader
import io.github.sekademi.spotufi.ui.theme.AppBackground
import io.github.sekademi.spotufi.ui.theme.AppPalette
import io.github.sekademi.spotufi.ui.components.SwipeToQueueBox
import io.github.sekademi.spotufi.ui.viewmodel.LikedSongsViewModel
import io.github.sekademi.spotufi.ui.viewmodel.PlayerViewModel

enum class LikedSortOption(val label: String) {
    DATE("Date added"),
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album")
}

fun LikedSortOption.getDescriptiveLabel(isDescending: Boolean): String {
    return when (this) {
        LikedSortOption.DATE -> if (isDescending) "Date added (newest to oldest)" else "Date added (oldest to newest)"
        LikedSortOption.TITLE -> if (isDescending) "Title (Z to A)" else "Title (A to Z)"
        LikedSortOption.ARTIST -> if (isDescending) "Artist (Z to A)" else "Artist (A to Z)"
        LikedSortOption.ALBUM -> if (isDescending) "Album (Z to A)" else "Album (A to Z)"
    }
}

private const val PREF_LIKED_SORTS = "LikedSongsSorts"

fun getLikedSortOption(context: Context): LikedSortOption {
    val prefs = context.getSharedPreferences(PREF_LIKED_SORTS, Context.MODE_PRIVATE)
    val saved = prefs.getString("sort_option", LikedSortOption.DATE.name)
    return runCatching { LikedSortOption.valueOf(saved!!) }.getOrDefault(LikedSortOption.DATE)
}

fun isLikedSortDescending(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREF_LIKED_SORTS, Context.MODE_PRIVATE)
    if (!prefs.contains("sort_descending")) {
        return true
    }
    return prefs.getBoolean("sort_descending", true)
}

fun setLikedSort(context: Context, option: LikedSortOption, descending: Boolean) {
    context.getSharedPreferences(PREF_LIKED_SORTS, Context.MODE_PRIVATE).edit()
        .putString("sort_option", option.name)
        .putBoolean("sort_descending", descending)
        .apply()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LikedSongsScreen(navController: NavController) {

    val likedSongsViewModel: LikedSongsViewModel = hiltViewModel()
    val songsResp by likedSongsViewModel.songs.collectAsState()
    val context = LocalContext.current

    val songs = (songsResp as? Response.Success)?.data.orEmpty()

    LaunchedEffect(songs) {
        if (songs.isNotEmpty()) {
            SongPlayer.prefetchList(songs.map { it.url }, context)
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var currentSort by remember { mutableStateOf(getLikedSortOption(context)) }
    var isDescending by remember { mutableStateOf(isLikedSortDescending(context)) }
    var showSortSheet by remember { mutableStateOf(false) }

    val filteredSongs = remember(songs, searchQuery, currentSort, isDescending) {
        val filtered = if (searchQuery.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.singer.contains(searchQuery, ignoreCase = true) ||
                    it.album.contains(searchQuery, ignoreCase = true)
            }
        }

        when (currentSort) {
            LikedSortOption.DATE -> if (isDescending) filtered else filtered.reversed()
            LikedSortOption.TITLE -> if (isDescending) filtered.sortedByDescending { it.title.lowercase() } else filtered.sortedBy { it.title.lowercase() }
            LikedSortOption.ARTIST -> if (isDescending) filtered.sortedByDescending { it.singer.lowercase() } else filtered.sortedBy { it.singer.lowercase() }
            LikedSortOption.ALBUM -> if (isDescending) filtered.sortedByDescending { it.album.lowercase() } else filtered.sortedBy { it.album.lowercase() }
        }
    }

    var menuSong by remember { mutableStateOf<io.github.sekademi.spotufi.data.entity.SongsModel?>(null) }
    menuSong?.let { sel ->
        io.github.sekademi.spotufi.ui.components.SongOptionsSheet(
            song = sel,
            navController = navController,
            context = context,
            onDismiss = {
                // If the song was unliked in the menu, drop it from the list right
                // away (the Spotify-side removal is already in flight).
                if (!io.github.sekademi.spotufi.data.preferences.isSongLiked(context, sel.id.toString())) {
                    likedSongsViewModel.removeLocally(sel.id)
                }
                menuSong = null
            },
        )
    }

    // Spotify's Liked Songs uses a purple → dark gradient.
    val likedColor = Color(0xFF5038A0)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
        if (songsResp is Response.Loading) {
            Loader()
            return@Surface
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    modifier = Modifier.padding(16.dp, 0.dp),
                    navigationIcon = {
                        Icon(
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { navController.navigateUp() },
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "",
                            tint = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                    ),
                    title = { Text(text = "") }
                )
            }
        ) {
            val playerViewModel = io.github.sekademi.spotufi.ui.viewmodel.sharedPlayerViewModel()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(AppBackground.toArgb()))
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(440.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(likedColor, Color(AppBackground.toArgb())),
                                    startY = -100f,
                                ),
                            ),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Spacer(modifier = Modifier.padding(25.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(230.dp)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(Color(0xFF8E6FE0), Color(0xFF3B2A82)),
                                        )
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = "",
                                    tint = Color.White,
                                    modifier = Modifier.size(90.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.padding(5.dp))
                        Text(
                            modifier = Modifier.padding(20.dp, 5.dp, 0.dp, 0.dp),
                            text = "Liked Songs",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            modifier = Modifier.padding(20.dp, 4.dp, 20.dp, 0.dp),
                            text = "${songs.size} songs",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(20.dp, 0.dp)
                        ) {
                            // Download all liked songs for offline playback.
                            var likedDownloaded by remember(songs) {
                                mutableStateOf(songs.isNotEmpty() && SongPlayer.allDownloaded(songs, context))
                            }
                            if (songs.isNotEmpty()) {
                                Icon(
                                    imageVector = if (likedDownloaded)
                                        Icons.Default.CheckCircle else ImageVector.vectorResource(R.drawable.ic_download),
                                    tint = if (likedDownloaded) Color(AppPalette.toArgb()) else Color.White,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            if (!likedDownloaded) {
                                                SongPlayer.downloadAll(songs, context)
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Downloading ${songs.size} tracks…",
                                                    android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        },
                                    contentDescription = "Download liked songs",
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                // Shuffle-play: start liked songs in random order.
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_player_shuffle),
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            likedSongsViewModel.startShuffled(songs)?.let { first ->
                                                SongPlayer.playSong(first.url, context)
                                                likedSongsViewModel.updateSongState(
                                                    first.coverUri,
                                                    first.title,
                                                    first.singer,
                                                    true,
                                                    first.id,
                                                    0,
                                                    "Liked Songs",
                                                )
                                            }
                                        },
                                    contentDescription = "Shuffle play",
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            // Always visible: pause when playing, resume when this
                            // list's track is paused, otherwise start from the top.
                            if (songs.isNotEmpty()) {
                                val playing = likedSongsViewModel.currentSongPlayingState.value
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(Color.White)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            when {
                                                playing -> likedSongsViewModel.setPlaying(false)
                                                songs.any { it.id == likedSongsViewModel.currentSongId.value } ->
                                                    likedSongsViewModel.setPlaying(true)
                                                else -> {
                                                    likedSongsViewModel.updateQueue(songs)
                                                    SongPlayer.playSong(songs[0].url, context)
                                                    likedSongsViewModel.updateSongState(
                                                        songs[0].coverUri,
                                                        songs[0].title,
                                                        songs[0].singer,
                                                        true,
                                                        songs[0].id,
                                                        0,
                                                        "Liked Songs"
                                                    )
                                                }
                                            }
                                        }
                                ) {
                                    Icon(
                                        modifier = Modifier.size(25.dp),
                                        tint = Color.Black,
                                        painter = painterResource(
                                            id = if (playing) R.drawable.ic_playing else R.drawable.play_svgrepo_com,
                                        ),
                                        contentDescription = if (playing) "Pause" else "Play"
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Search bar for liked songs ──
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp, 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .height(55.dp)
                            .background(Color(0xFF242424))
                            .padding(10.dp, 0.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search_big),
                            tint = Color.White,
                            contentDescription = "Search",
                            modifier = Modifier.size(24.dp)
                        )

                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle.Default.copy(
                                fontSize = 16.sp,
                                color = Color.White,
                                fontWeight = FontWeight(500)
                            ),
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Color.White
                            ),
                            singleLine = true,
                            placeholder = {
                                Text(
                                    text = "Search in Liked Songs",
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )

                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { searchQuery = "" }
                            )
                        }
                    }
                }

                // ── Sort action ──
                item {
                    if (songs.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp, 0.dp, 20.dp, 8.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF2A2A30))
                                    .clickable { showSortSheet = true }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = currentSort.getDescriptiveLabel(isDescending),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Sort Options",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }

                itemsIndexed(filteredSongs, key = { _, song -> song.id }) { index, song ->
                    val currentColor = if (song.id == likedSongsViewModel.currentSongId.value)
                        Color(AppPalette.toArgb()) else Color.White

                    SwipeToQueueBox(song = song, onAddToQueue = { playerViewModel.addToQueue(it) }) {
                    Row(
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp, 8.dp)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onLongClick = { menuSong = song },
                                onClick = {
                                    likedSongsViewModel.updateQueue(filteredSongs)
                                    SongPlayer.playSong(song.url, context)
                                    likedSongsViewModel.updateSongState(
                                        song.coverUri,
                                        song.title,
                                        song.singer,
                                        true,
                                        song.id,
                                        index,
                                        "Liked Songs"
                                    )
                                },
                            )
                    ) {
                        AsyncImage(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            model = song.coverUri,
                            error = painterResource(R.drawable.placeholder),
                            contentScale = ContentScale.Crop,
                            contentDescription = ""
                        )
                        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(
                                text = song.title,
                                color = currentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Text(
                                text = song.singer,
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                    }
                }

                item { Spacer(modifier = Modifier.padding(80.dp)) }
            }
        }

        if (showSortSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSortSheet = false },
                containerColor = Color(0xFF1A1A1A)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Text(
                        text = "Sort by",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 12.dp)
                    )
                    HorizontalDivider(color = Color(0xFF2A2A2A))
                    Spacer(modifier = Modifier.height(4.dp))
                    LikedSortOption.entries.forEach { option ->
                        val isSelected = option == currentSort
                        val icon = when (option) {
                            LikedSortOption.DATE -> Icons.Default.DateRange
                            LikedSortOption.TITLE -> Icons.AutoMirrored.Filled.List
                            LikedSortOption.ARTIST -> Icons.Default.Person
                            LikedSortOption.ALBUM -> Icons.Default.Menu
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val nextDesc = if (currentSort == option) {
                                        !isDescending
                                    } else {
                                        option == LikedSortOption.DATE
                                    }
                                    currentSort = option
                                    isDescending = nextDesc
                                    setLikedSort(context, option, nextDesc)
                                    showSortSheet = false
                                }
                                .padding(16.dp, 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) Color(AppPalette.toArgb()) else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(18.dp))
                            Text(
                                text = if (isSelected) option.getDescriptiveLabel(isDescending) else option.getDescriptiveLabel(option == LikedSortOption.DATE),
                                color = if (isSelected) Color(AppPalette.toArgb()) else Color.White,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = if (isDescending) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = Color(AppPalette.toArgb()),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

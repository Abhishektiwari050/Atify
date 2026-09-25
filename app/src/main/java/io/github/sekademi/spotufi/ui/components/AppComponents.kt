package io.github.sekademi.spotufi.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.util.lerp
import io.github.sekademi.spotufi.data.entity.SongsModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import io.github.sekademi.spotufi.R
import io.github.sekademi.spotufi.data.preferences.addLikedSongId
import io.github.sekademi.spotufi.data.preferences.isSongLiked
import io.github.sekademi.spotufi.data.preferences.removeLikedSongId
import io.github.sekademi.spotufi.di.PaletteExtractor
import io.github.sekademi.spotufi.di.SongPlayer
import io.github.sekademi.spotufi.ui.navigation.Routes
import io.github.sekademi.spotufi.ui.theme.AppBackground
import io.github.sekademi.spotufi.ui.theme.GridBackground
import io.github.sekademi.spotufi.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Loader() {
    Column(Modifier
        .background(Color(AppBackground.toArgb())),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(45.dp),
            color = Color(0xFF4A4AC4)
        )
    }

}

@OptIn(ExperimentalFoundationApi::class)
private enum class MiniPlayerDragAxis { NONE, HORIZONTAL, VERTICAL }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(navController: NavHostController) {
    val miniPlayerViewModel = io.github.sekademi.spotufi.ui.viewmodel.sharedPlayerViewModel()
    val songTitle = miniPlayerViewModel.currentSongTitle.value
    val songSinger = miniPlayerViewModel.currentSongSinger.value
    val songCoverUri = miniPlayerViewModel.currentSongCoverUri.value
    var songPlayingState = miniPlayerViewModel.currentSongPlayingState.value
    val songId = miniPlayerViewModel.currentSongId.value
    val songIndex = miniPlayerViewModel.currentSongIndex.value
    val songAlbum = miniPlayerViewModel.currentSongAlbum.value

    val haptic = LocalHapticFeedback.current
    var swipeOffsetY by remember { mutableFloatStateOf(0f) }
    var swipeOffsetX by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    var songProgress by remember { mutableFloatStateOf(0f) }

    songProgress = if (SongPlayer.getDuration() > 0) {
        SongPlayer.getCurrentPosition().toFloat() / SongPlayer.getDuration().toFloat()
    } else {
        0f
    }

    LaunchedEffect(songId) {
        swipeOffsetX = 0f
    }

    val currentRoute = navController.currentBackStackEntry?.destination?.route

    LaunchedEffect(key1 = songPlayingState) {
        while (songPlayingState) {
            songProgress = SongPlayer.getDuration().toFloat().let { dur ->
                if (dur > 0f) (SongPlayer.getCurrentPosition().toFloat() / dur).coerceIn(0f, 1f) else 0f
            }
            delay(300L)
        }
    }

    val context = LocalContext.current

    var darkVibrantColor by remember {
        mutableStateOf(Color(GridBackground.toArgb()))
    }
    LaunchedEffect(songCoverUri) {
        PaletteExtractor.extractFirstColorFromImageUrl(context = context, imageUrl = songCoverUri) { color ->
            darkVibrantColor = color
        }
    }

    var isLiked by remember {
        mutableStateOf(false)
    }
    val likeState = miniPlayerViewModel.likeState.value
    LaunchedEffect(likeState, songId) {
        isLiked = isSongLiked(context, songId.toString())
    }
    val currentTrack = miniPlayerViewModel.queue.value.firstOrNull { it.id == songId }
    var showSavedIn by remember { mutableStateOf(false) }
    if (showSavedIn && currentTrack != null) {
        SavedInSheet(
            song = currentTrack,
            context = context,
            onDismiss = { showSavedIn = false },
            onLikedChanged = {
                isLiked = it
                miniPlayerViewModel.updateLikeState(!likeState)
            },
        )
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .padding(bottom = 4.dp)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .background(darkVibrantColor)
            .graphicsLayer {
                translationY = swipeOffsetY
                alpha = (1f - (kotlin.math.abs(swipeOffsetY) / 250f)).coerceIn(0f, 1f)
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(start = 10.dp, end = 12.dp)
        ) {
            // Interactive Track Info (Artwork + Title + Artist)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        val touchSlop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var totalDx = 0f
                            var totalDy = 0f
                            var hasPassedSlop = false
                            var dragAxis = MiniPlayerDragAxis.NONE
                            val distThresholdY = 30.dp.toPx()
                            val thresholdX = 40.dp.toPx()

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                if (change.changedToUp()) {
                                    if (!hasPassedSlop) {
                                        // Quick tap anywhere on track info row -> open player immediately
                                        navController.navigate(Routes.Player.route)
                                    } else {
                                        when (dragAxis) {
                                            MiniPlayerDragAxis.VERTICAL -> {
                                                if (swipeOffsetY < -distThresholdY) {
                                                    // Swipe up -> open player
                                                    coroutineScope.launch {
                                                        Animatable(swipeOffsetY).animateTo(-250f, tween(180)) { swipeOffsetY = value }
                                                        swipeOffsetY = 0f
                                                        navController.navigate(Routes.Player.route)
                                                    }
                                                } else if (swipeOffsetY > distThresholdY) {
                                                    // Swipe down -> dismiss/close mini player & pause
                                                    coroutineScope.launch {
                                                        Animatable(swipeOffsetY).animateTo(250f, tween(180)) { swipeOffsetY = value }
                                                        swipeOffsetY = 0f
                                                        miniPlayerViewModel.clearSong()
                                                    }
                                                } else {
                                                    coroutineScope.launch {
                                                        Animatable(swipeOffsetY).animateTo(0f, spring(0.75f, 400f)) { swipeOffsetY = value }
                                                    }
                                                }
                                            }
                                            MiniPlayerDragAxis.HORIZONTAL -> {
                                                val activeQueue = miniPlayerViewModel.queue.value
                                                if (swipeOffsetX > thresholdX) {
                                                    // Swiped right -> play next track (forward skip)
                                                    coroutineScope.launch {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        val startX = swipeOffsetX
                                                        Animatable(startX).animateTo(350f, tween(140, easing = FastOutSlowInEasing)) { swipeOffsetX = value }
                                                        miniPlayerViewModel.playNextSongs(activeQueue, context)
                                                        swipeOffsetX = -350f
                                                        Animatable(-350f).animateTo(0f, spring(0.8f, 400f)) { swipeOffsetX = value }
                                                    }
                                                } else if (swipeOffsetX < -thresholdX) {
                                                    // Swiped left -> play previous track
                                                    coroutineScope.launch {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        val startX = swipeOffsetX
                                                        Animatable(startX).animateTo(-350f, tween(140, easing = FastOutSlowInEasing)) { swipeOffsetX = value }
                                                        miniPlayerViewModel.playPreviousSong(activeQueue, context)
                                                        swipeOffsetX = 350f
                                                        Animatable(350f).animateTo(0f, spring(0.8f, 400f)) { swipeOffsetX = value }
                                                    }
                                                } else {
                                                    coroutineScope.launch {
                                                        Animatable(swipeOffsetX).animateTo(0f, spring(0.75f, 500f)) { swipeOffsetX = value }
                                                    }
                                                }
                                            }
                                            MiniPlayerDragAxis.NONE -> {
                                                navController.navigate(Routes.Player.route)
                                            }
                                        }
                                    }
                                    break
                                }

                                val dragAmount = change.positionChange()
                                totalDx += dragAmount.x
                                totalDy += dragAmount.y

                                if (!hasPassedSlop) {
                                    val distance = kotlin.math.hypot(totalDx, totalDy)
                                    if (distance > touchSlop) {
                                        hasPassedSlop = true
                                        dragAxis = if (kotlin.math.abs(totalDx) >= kotlin.math.abs(totalDy)) {
                                            MiniPlayerDragAxis.HORIZONTAL
                                        } else {
                                            MiniPlayerDragAxis.VERTICAL
                                        }
                                    }
                                }

                                if (hasPassedSlop) {
                                    change.consume()
                                    when (dragAxis) {
                                        MiniPlayerDragAxis.VERTICAL -> {
                                            swipeOffsetY += dragAmount.y
                                        }
                                        MiniPlayerDragAxis.HORIZONTAL -> {
                                            swipeOffsetX += dragAmount.x
                                        }
                                        MiniPlayerDragAxis.NONE -> {}
                                    }
                                }
                            }
                        }
                    }
                    .graphicsLayer {
                        translationX = swipeOffsetX
                        alpha = (1f - (kotlin.math.abs(swipeOffsetX) / 450f)).coerceIn(0.25f, 1f)
                    }
                    .clipToBounds()
            ) {
                AsyncImage(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    model = songCoverUri,
                    contentScale = ContentScale.Crop,
                    error = painterResource(R.drawable.placeholder),
                    placeholder = painterResource(R.drawable.placeholder),
                    contentDescription = ""
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = songTitle,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = songSinger,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000),
                    )
                }
            }

            // Fixed Action Controls on the right
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                // Plus / Check icon with touch target
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLiked) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            tint = Color(0xFF1ED760),
                            modifier = Modifier
                                .size(24.dp)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        removeLikedSongId(context, songId.toString())
                                        isLiked = false
                                        miniPlayerViewModel.updateLikeState(!likeState)
                                    },
                                    onLongClick = { showSavedIn = true },
                                ),
                            contentDescription = "Saved",
                        )
                    } else {
                        Icon(
                            modifier = Modifier
                                .size(24.dp)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        addLikedSongId(context, songId.toString())
                                        isLiked = true
                                        miniPlayerViewModel.updateLikeState(!likeState)
                                        io.github.sekademi.spotufi.data.api.SpotifySync.setTrackSaved(
                                            context, currentTrack?.spotifyTrackId.orEmpty(), true)
                                    },
                                    onLongClick = { showSavedIn = true },
                                ),
                            painter = painterResource(id = R.drawable.ic_add),
                            tint = Color.White,
                            contentDescription = "Add",
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                val isLocatingOrBuffering = miniPlayerViewModel.isResolving.value || miniPlayerViewModel.isBuffering.value
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    if (isLocatingOrBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            painter = if (songPlayingState)
                                painterResource(id = R.drawable.ic_playing)
                            else
                                painterResource(id = R.drawable.play_svgrepo_com),
                            contentDescription = if (songPlayingState) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier
                                .size(32.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (songPlayingState) {
                                        SongPlayer.pause()
                                        miniPlayerViewModel.updateSongState(
                                            songCoverUri,
                                            songTitle,
                                            songSinger,
                                            false,
                                            songId,
                                            songIndex,
                                            songAlbum
                                        )
                                    } else {
                                        SongPlayer.play()
                                        miniPlayerViewModel.updateSongState(
                                            songCoverUri,
                                            songTitle,
                                            songSinger,
                                            true,
                                            songId,
                                            songIndex,
                                            songAlbum
                                        )
                                    }
                                }
                        )
                    }
                }
            }
        }

        // Sleek 2.5dp progress bar flush with bottom edge
        val animatedProgress by animateFloatAsState(
            targetValue = songProgress,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "miniPlayerProgress"
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .background(Color(0x33FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = animatedProgress)
                    .background(Color.White)
            )
        }
    }
}


@Composable
fun CustomSlider(
    modifier: Modifier = Modifier,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
        .coerceIn(0f, 1f)
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .height(14.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val mapped = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                    onValueChange(mapped)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val newFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val mapped = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                    onValueChange(mapped)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
            val trackHeightPx = with(density) { 2.dp.toPx() }
            val trackY = size.height / 2f
            val thumbX = fraction * size.width

            drawLine(
                color = Color(0xFF535353),
                start = Offset(0f, trackY),
                end = Offset(size.width, trackY),
                strokeWidth = trackHeightPx,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(0f, trackY),
                end = Offset(thumbX, trackY),
                strokeWidth = trackHeightPx,
                cap = StrokeCap.Round
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToQueueBox(
    song: SongsModel,
    onAddToQueue: (SongsModel) -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dismissState = rememberSwipeToDismissBoxState()

    val dismissProgress = dismissState.progress
    val isSettling = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd
    val progress = if (isSettling) dismissProgress.coerceIn(0f, 1f) else 0f

    val iconTint by animateColorAsState(
        targetValue = if (progress > 0.3f) Color.White else Color.Gray,
        label = "IconTintAnimation"
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        onDismiss = {
            onAddToQueue(song)
            android.widget.Toast.makeText(
                context,
                "${song.title} added to queue",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            scope.launch { dismissState.reset() }
        },
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1DB954).copy(alpha = progress * 0.4f))
                    .padding(horizontal = 24.dp)
            ) {
                if (progress > 0.05f) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_queue_add),
                            contentDescription = "Add to queue",
                            tint = iconTint,
                            modifier = Modifier
                                .size(24.dp)
                                .scale(lerp(0.8f, 1.1f, progress))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add to queue",
                            color = iconTint,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        },
        content = content,
    )
}

@Composable
fun Snackbar(showMessage : String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ){
        Text(
            fontWeight = FontWeight.W500,
            fontSize = 14.sp,
            color = Color.Black,
            text = showMessage
        )
    }
}
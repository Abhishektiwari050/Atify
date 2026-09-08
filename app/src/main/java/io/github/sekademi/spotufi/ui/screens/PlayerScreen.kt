package io.github.sekademi.spotufi.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.vectorResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import io.github.sekademi.spotufi.R
import io.github.sekademi.spotufi.data.api.Response
import io.github.sekademi.spotufi.data.entity.SongsModel
import io.github.sekademi.spotufi.data.preferences.addLikedSongId
import io.github.sekademi.spotufi.data.preferences.alternativeStreamKey
import io.github.sekademi.spotufi.data.preferences.clearAlternativeStream
import io.github.sekademi.spotufi.data.preferences.getAlternativeStream
import io.github.sekademi.spotufi.data.preferences.getLikedSongIds
import io.github.sekademi.spotufi.data.preferences.getSongsByIds
import io.github.sekademi.spotufi.data.preferences.isSongLiked
import io.github.sekademi.spotufi.data.preferences.removeLikedSongId
import io.github.sekademi.spotufi.data.preferences.setLocalAlternativeStream
import io.github.sekademi.spotufi.data.preferences.setYouTubeAlternativeStream
import io.github.sekademi.spotufi.di.Palette
import io.github.sekademi.spotufi.di.SongPlayer
import io.github.sekademi.spotufi.di.RepeatMode
import io.github.sekademi.spotufi.ui.components.Snackbar
import io.github.sekademi.spotufi.ui.navigation.Routes
import io.github.sekademi.spotufi.ui.navigation.albumRoute
import io.github.sekademi.spotufi.ui.navigation.artistRoute
import io.github.sekademi.spotufi.ui.theme.AppBackground
import io.github.sekademi.spotufi.ui.theme.AppPalette
import io.github.sekademi.spotufi.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import io.github.sekademi.spotufi.ui.screens.player.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.media3.common.Player
import androidx.media3.ui.compose.material3.buttons.PlayPauseButton
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(navController: NavController) {
    val density = LocalDensity.current
    val screenHeight = with(density) {
        val dpHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
        if (dpHeight.value > 0f) dpHeight.toPx() else 2000f
    }
    val coroutineScope = rememberCoroutineScope()

    // offsetY represents the current translation offset of the player screen.
    // It starts at screenHeight (so the screen initially renders fully off-screen)
    // and animates up to 0f.
    var offsetY by remember { mutableFloatStateOf(screenHeight) }
    val animatable = remember { Animatable(screenHeight) }

    var animationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Track whether the entrance animation has completed so the auto-dismiss
    // safety-net doesn't fire on the initial off-screen state.
    var hasAppeared by remember { mutableStateOf(false) }

    suspend fun slideTo(targetValue: Float, velocity: Float = 0f) {
        try {
            animatable.snapTo(offsetY)
            animatable.animateTo(
                targetValue = targetValue,
                initialVelocity = velocity,
                animationSpec = if (targetValue == 0f || targetValue == screenHeight)
                    tween(350) else spring()
            ) {
                offsetY = this.value
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
            // Animation was cancelled — offsetY is wherever the Animatable stopped.
        }
    }

    fun cancelRunningAnimation() {
        animationJob?.cancel()
        animationJob = null
    }

    fun launchAnimation(targetValue: Float, velocity: Float = 0f) {
        cancelRunningAnimation()
        animationJob = coroutineScope.launch {
            slideTo(targetValue, velocity)
        }
    }

    // ── Safety net: if offsetY ever reaches the bottom after the player has
    //    opened, dismiss regardless of how it got there. ──
    LaunchedEffect(offsetY) {
        if (hasAppeared && offsetY >= screenHeight - 1f) {
            navController.navigateUp()
        }
    }

    // ── Configure dialog window for edge-to-edge ──
    // `decorFitsSystemWindows = false` in DialogProperties does NOT actually
    // make a Compose Navigation dialog draw behind system bars (known unfixed
    // issue).  The proven workaround is to copy the Activity window's
    // LayoutParams onto the dialog window and resize the dialog's parent view
    // to fill the screen — see https://stackoverflow.com/a/75768025
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        // Walk up the view tree to find the dialog window.
        var dialogWindow: android.view.Window? = null
        var v: android.view.View? = view
        while (v != null) {
            if (v is androidx.compose.ui.window.DialogWindowProvider) {
                dialogWindow = v.window
                break
            }
            val parent = v.parent
            if (parent is android.view.View) {
                v = parent
            } else {
                if (parent is androidx.compose.ui.window.DialogWindowProvider) {
                    dialogWindow = parent.window
                    break
                }
                break
            }
        }
        // Get the Activity window through the context (works even inside a dialog).
        val activityWindow = generateSequence<android.content.Context>(view.context) { ctx ->
            (ctx as? android.content.ContextWrapper)?.baseContext
        }.filterIsInstance<android.app.Activity>().firstOrNull()?.window

        if (activityWindow != null && dialogWindow != null) {
            // Copy the Activity's window attributes (which already have
            // edge-to-edge configured) onto the dialog window.
            val attrs = android.view.WindowManager.LayoutParams()
            attrs.copyFrom(activityWindow.attributes)
            attrs.type = dialogWindow.attributes.type
            dialogWindow.attributes = attrs
            // Resize the dialog's parent view to fill the screen.
            val parentView = view.parent as? android.view.View
            parentView?.layoutParams = android.widget.FrameLayout.LayoutParams(
                activityWindow.decorView.width,
                activityWindow.decorView.height
            )
            // Make bars transparent.
            WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
            WindowInsetsControllerCompat(dialogWindow, dialogWindow.decorView).isAppearanceLightStatusBars = false
        }
    }

    // Animate the player sliding up when first opened
    LaunchedEffect(Unit) {
        slideTo(0f)
        hasAppeared = true
    }

    // Function to handle sliding down the player and popping the backstack
    val dismissPlayer: () -> Unit = {
        launchAnimation(screenHeight)
    }

    // Intercept hardware system back press to slide player down smoothly
    BackHandler {
        dismissPlayer()
    }

    // Create nested scroll connection to handle drag gestures
    val nestedScrollConnection = remember(screenHeight) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                // Only cancel ongoing slide animations when the USER is physically
                // dragging.  Fling-driven scroll events must not interfere.
                if (source == NestedScrollSource.UserInput) {
                    cancelRunningAnimation()
                }
                // If the player is currently offset (offsetY > 0) and the user
                // drags up (delta < 0), consume the drag to slide the player back
                // up towards 0.
                if (offsetY > 0f && delta < 0f) {
                    val newOffset = (offsetY + delta).coerceIn(0f, screenHeight)
                    offsetY = newOffset
                    return Offset(0f, delta)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val delta = available.y
                if (source == NestedScrollSource.UserInput) {
                    cancelRunningAnimation()
                }
                // Unconsumed downward scroll (delta > 0) because the list is at
                // the top — translate the player screen down.
                if (delta > 0f) {
                    offsetY = (offsetY + delta).coerceIn(0f, screenHeight)
                    return Offset(0f, delta)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // When the drag is released, if the player is offset, animate it
                // to either 0f (open) or screenHeight (dismiss).
                if (offsetY > 0f) {
                    val targetValue = when {
                        available.y < -500f -> 0f
                        available.y > 500f -> screenHeight
                        else -> if (offsetY > screenHeight * 0.25f) screenHeight else 0f
                    }
                    val job = coroutineScope.launch {
                        slideTo(targetValue, available.y)
                    }
                    animationJob = job
                    try {
                        job.join()
                    } catch (_: Exception) { }
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val playerViewModel = io.github.sekademi.spotufi.ui.viewmodel.sharedPlayerViewModel()
    val songTitle = playerViewModel.currentSongTitle.value
    val songSinger = playerViewModel.currentSongSinger.value
    val songCoverUri = playerViewModel.currentSongCoverUri.value
    val songPlayingState = playerViewModel.currentSongPlayingState.value
    val songId = playerViewModel.currentSongId.value
    val context = LocalContext.current
    val isLiked = remember {
        mutableStateOf(isSongLiked(context, songId.toString()))
    }
    var showMenu by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSavedIn by remember { mutableStateOf(false) }

    if (showMenu) {
        PlayerOptionsSheet(
            navController = navController,
            playerViewModel = playerViewModel,
            context = context,
            isLiked = isLiked,
            onDismiss = { showMenu = false }
        )
    }

    if (showSavedIn) {
        playerViewModel.queue.value.firstOrNull { it.id == songId }?.let { track ->
            io.github.sekademi.spotufi.ui.components.SavedInSheet(
                song = track,
                context = context,
                onDismiss = { showSavedIn = false },
                onLikedChanged = { isLiked.value = it },
            )
        } ?: run { showSavedIn = false }
    }


    var songProgress by remember { mutableStateOf(maxOf(0f, SongPlayer.getCurrentPosition().toFloat())) }
    var songDurationText by remember { mutableStateOf("0") }
    var songProgressText by remember { mutableStateOf("") }

    songDurationText = if (SongPlayer.getDuration() < 0){
        "0:00"
    }
    else{
        playerViewModel.formatDuration(SongPlayer.getDuration())
    }
    songProgressText = if (SongPlayer.getCurrentPosition() < 0){
        "0:00"
    }
    else{
        playerViewModel.formatDuration(SongPlayer.getCurrentPosition())
    }

    Log.d("checkplayer", songTitle)

    //playerViewModel.updateSongState(songCoverUri, songTitle, songSinger, songPlayingState)



    var dominentColor by remember {
        mutableStateOf(Color(AppBackground.toArgb()))
    }
    Palette().extractSecondColorFromCoverUrl(context = context, songCoverUri){ color ->
        dominentColor = color
    }

    val shuffle = playerViewModel.shuffleState.value
    val repeat = playerViewModel.repeatState.value

    // The queue is whatever list the user actually started playing (album tracks,
    // search results, liked songs) — stored when the song was tapped. Falling back
    // to the global top-tracks feed used to crash / be empty (it's rate-limited).
    val queueSongs = playerViewModel.queue.value

    // ── Now-playing swipe pager ──
    // Index of the playing track in the queue (fallback to 0 so the pager is valid
    // even before the queue/current id line up).
    val currentIndex = queueSongs.indexOfFirst { it.id == playerViewModel.currentSongId.value }
        .let { if (it >= 0) it else 0 }
    val artworkPagerState = rememberPagerState(
        initialPage = currentIndex,
        pageCount = { queueSongs.size.coerceAtLeast(1) },
    )
    // External track changes (auto-advance, prev/next buttons, queue edits) → snap the
    // pager to the new track. Guard on settled state so we don't fight an in-progress swipe.
    LaunchedEffect(currentIndex, queueSongs.size) {
        if (currentIndex in 0 until queueSongs.size &&
            artworkPagerState.currentPage != currentIndex &&
            !artworkPagerState.isScrollInProgress
        ) {
            artworkPagerState.scrollToPage(currentIndex)
        }
    }
    // User settled the pager on a different page → play that track. Compare against the
    // live current id (not currentIndex captured above) to avoid a replay feedback loop.
    LaunchedEffect(artworkPagerState, queueSongs) {
        snapshotFlow { artworkPagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                queueSongs.getOrNull(page)?.let { target ->
                    if (target.id != playerViewModel.currentSongId.value) {
                        playerViewModel.playSongAt(queueSongs, page, context)
                        isLiked.value = isSongLiked(context, target.id.toString())
                    }
                }
            }
    }

    // Warm the stream cache for the adjacent tracks so next/previous start instantly.
    LaunchedEffect(playerViewModel.currentSongId.value, queueSongs) {
        val idx = queueSongs.indexOfFirst { it.id == playerViewModel.currentSongId.value }
        if (idx >= 0) {
            queueSongs.getOrNull(idx + 1)?.let { SongPlayer.prefetch(it.url, context) }
            queueSongs.getOrNull(idx - 1)?.let { SongPlayer.prefetch(it.url, context) }
        }
    }

    // Load the current track's Spotify Canvas (full-screen looping video background).
    LaunchedEffect(playerViewModel.currentSongId.value, queueSongs) {
        val track = queueSongs.firstOrNull { it.id == playerViewModel.currentSongId.value }
        playerViewModel.loadCanvas(track?.spotifyTrackId.orEmpty())
    }




    LaunchedEffect(playerViewModel.currentSongId.value, songPlayingState) {
        while (true) {
            val dur = SongPlayer.getDuration()
            songDurationText = if (dur < 0) "0:00" else playerViewModel.formatDuration(dur)
            val pos = SongPlayer.getCurrentPosition()
            songProgress = pos.toFloat()
            songProgressText = if (pos < 0) "0:00" else playerViewModel.formatDuration(pos)

            if (songPlayingState) {
                delay(300L)
            } else {
                if (dur > 0) {
                    delay(2000L)
                } else {
                    delay(300L)
                }
            }
        }
    }









    val canvasUrl = playerViewModel.canvasUrl.value

    DisposableEffect(SongPlayer.exoPlayer) {
        val p = SongPlayer.exoPlayer ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playerViewModel.updateSongState(
                    playerViewModel.currentSongCoverUri.value,
                    playerViewModel.currentSongTitle.value,
                    playerViewModel.currentSongSinger.value,
                    playWhenReady,
                    playerViewModel.currentSongId.value,
                    playerViewModel.currentSongIndex.value,
                    playerViewModel.currentSongAlbum.value,
                )
            }
        }
        p.addListener(listener)
        onDispose { p.removeListener(listener) }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val anyPressed = event.changes.any { it.pressed }
                        if (anyPressed) {
                            if (animationJob != null) {
                                cancelRunningAnimation()
                            }
                        }
                    }
                }
            }
            .graphicsLayer {
                translationY = offsetY
                alpha = (1f - (offsetY / screenHeight)).coerceIn(0f, 1f)
            }
            .background(
                Brush.verticalGradient(
                    colors = listOf(dominentColor, Color.Black),
                    startY = 100f
                )
            )
    ) {
        if (canvasUrl != null) {
            // Spotify Canvas: the looping video fills the whole now-playing screen
            // edge-to-edge behind the controls (the "immersive" treatment), with a
            // scrim on top so the title, slider and buttons stay readable.
            CanvasVideo(
                url = canvasUrl,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.50f),
                                Color.Black.copy(alpha = 0.10f),
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.80f),
                            )
                        )
                    )
            )
        }
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        item {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillParentMaxHeight()
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.statusBarsPadding())
            PlayerTopBar(
                navController = navController,
                onMenuClick = { showMenu = true },
                contextName = playerViewModel.currentSongAlbum.value,
                onBackClick = { dismissPlayer() }
            )
            //Spacer(modifier = Modifier.padding(16.dp))
            // Swipe the artwork left/right to skip to the next/previous track. Using a
            // HorizontalPager makes the artwork follow the finger and snap, syncing the
            // change with the track (Spotify's now-playing gesture) instead of an abrupt
            // swipe-then-switch. When the queue is empty fall back to a static image.
            // When a Canvas is playing it fills the screen behind this column, so the
            // artwork is hidden (alpha 0) rather than removed — the pager stays in
            // the layout so the swipe-to-skip gesture keeps working over the video.
            // The artwork is the FLEXIBLE part of the screen (weight), capped at its
            // old 385dp size. On short/scaled displays the fixed-size version pushed
            // the slider and playback buttons off the bottom of the screen; now the
            // artwork shrinks instead and the controls always fit.
            PlayerArtwork(
                queueSongs = queueSongs,
                currentCoverUri = songCoverUri,
                pagerState = artworkPagerState,
                canvasUrl = canvasUrl,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .height(300.dp)
                    .padding(0.dp, 0.dp, 0.dp, 50.dp)
            ){
                // Reads each 300ms tick (songProgress recomposition) so it reflects
                // the current engine — Spotify vs Lossless (SpotiFLAC) vs YouTube.
                PlayerInfo(
                    songTitle, songSinger, songId, context, isLiked,
                    source = SongPlayer.currentSource,
                    quality = SongPlayer.currentQuality,
                    isResolving = playerViewModel.isResolving.value,
                    resolveStatus = playerViewModel.resolveStatus.value,
                    resolveError = playerViewModel.resolveError.value,
                    onArtistClick = {
                        val track = queueSongs.firstOrNull { it.id == songId }
                        playerViewModel.goToArtist(track?.spotifyTrackId.orEmpty(), songSinger) { route ->
                            navController.navigate(route)
                        }
                    },
                    spotifyTrackId = queueSongs.firstOrNull { it.id == songId }?.spotifyTrackId.orEmpty(),
                    onShowSavedIn = { showSavedIn = true },
                )

                PlayerProgress(
                    songDurationText = songDurationText,
                    songProgressText = songProgressText,
                    songPlayingState = songPlayingState,
                    playerViewModel = playerViewModel,
                )

                Spacer(modifier = Modifier.padding(5.dp))
                PlayerFull(songPlayingState, playerViewModel, context, isLiked, shuffle, repeat, queueSongs)
            }

            // Spotify-style bottom row: current audio device (Connect) on the left,
            // share + queue on the right.
            PlayerConnectRow(
                navController = navController,
                context = context,
                currentTrack = queueSongs.firstOrNull { it.id == playerViewModel.currentSongId.value },
            )

            //PlayerEndInfo()
        }
        }
        item {
            InlineLyrics(
                title = songTitle,
                artist = songSinger,
                album = playerViewModel.currentSongAlbum.value,
                accentColor = dominentColor,
                onExpand = { showLyrics = true },
            )
        }
        }

        if (showLyrics) {
            LyricsScreen(
                title = songTitle,
                artist = songSinger,
                album = playerViewModel.currentSongAlbum.value,
                accentColor = dominentColor,
                onClose = { showLyrics = false }
            )
        }
    }
}

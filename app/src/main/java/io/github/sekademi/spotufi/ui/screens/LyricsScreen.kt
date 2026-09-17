package io.github.sekademi.spotufi.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.res.painterResource
import io.github.sekademi.spotufi.R
import io.github.sekademi.spotufi.data.entity.Lyrics
import io.github.sekademi.spotufi.di.SongPlayer
import io.github.sekademi.spotufi.ui.components.KaraokeControlSheet
import io.github.sekademi.spotufi.ui.viewmodel.LyricsViewModel
import kotlinx.coroutines.delay

/** Polls ExoPlayer's position every 60ms so the active lyric line and syllable progress track fluidly. */
@Composable
private fun rememberPlaybackPositionMs(): State<Long> {
    val pos = remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            pos.value = SongPlayer.getCurrentPosition().coerceAtLeast(0L)
            delay(60L)
        }
    }
    return pos
}

private fun activeIndexFor(lyrics: Lyrics, positionMs: Long): Int =
    if (lyrics.synced) lyrics.lines.indexOfLast { it.timeMs <= positionMs + 250 }.coerceAtLeast(0)
    else -1

/** Seek to a tapped synced line and make sure we're playing. */
private fun jumpTo(timeMs: Long) {
    SongPlayer.seekTo(timeMs)
    if (!SongPlayer.isPlaying()) SongPlayer.play()
}

/**
 * Full-screen synced-lyrics overlay (Spotify "Lyrics" view). The current line is
 * highlighted bright and the list auto-scrolls to keep it centered; tapping a line
 * jumps playback to it (synced lyrics only). Falls back to a static scroll for plain
 * (un-timed) lyrics.
 */
@Composable
fun LyricsScreen(
    title: String,
    artist: String,
    album: String,
    accentColor: Color,
    coverUrl: String = "",
    onClose: () -> Unit,
) {
    val vm: LyricsViewModel = hiltViewModel()
    LaunchedEffect(title, artist) {
        val durationSec = (SongPlayer.getDuration() / 1000).toInt()
        vm.load(title, artist, album, durationSec)
    }
    val state by vm.state.collectAsState()
    val positionMs by rememberPlaybackPositionMs()
    var showKaraokeSheet by remember { mutableStateOf(false) }
    var showQuoteCardDialog by remember { mutableStateOf(false) }
    var showPitchGuide by remember { mutableStateOf(false) }
    var showTransliteration by remember { mutableStateOf(true) }
    val attenuation by SongPlayer.vocalAttenuation.collectAsState()
    val isKaraokeActive = attenuation > 0.01f

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Solid base first — the gradient's translucent middle stop let the
            // player screen bleed through, making the lyrics page look transparent.
            .background(Color(0xFF121212))
            .background(
                Brush.verticalGradient(
                    colors = listOf(accentColor, accentColor.copy(alpha = 0.55f), Color.Black),
                )
            )
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Lyrics", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Pitch Guide toggle button
                Box(
                    modifier = Modifier
                        .background(
                            if (showPitchGuide) Color(0xFF1ED760).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showPitchGuide = !showPitchGuide }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "♫ Pitch",
                        color = if (showPitchGuide) Color(0xFF1ED760) else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(10.dp))

                // Transliteration toggle button
                Box(
                    modifier = Modifier
                        .background(
                            if (showTransliteration) Color(0xFF1ED760).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showTransliteration = !showTransliteration }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "A⇄文",
                        color = if (showTransliteration) Color(0xFF1ED760) else Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(10.dp))

                // Share Lyrics Quote Card button
                if (state is LyricsViewModel.State.Loaded) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_share),
                        contentDescription = "Share Quote Card",
                        tint = Color.White,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showQuoteCardDialog = true },
                            ),
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(12.dp))
                }
                // Karaoke / Sing-Along microphone button
                Icon(
                    painter = painterResource(id = R.drawable.ic_microphone),
                    contentDescription = "Sing Along Karaoke",
                    tint = if (isKaraokeActive) Color(0xFF1ED760) else Color.White,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showKaraokeSheet = !showKaraokeSheet },
                        ),
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(12.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onClose() }
                )
            }
        }

        if (showPitchGuide) {
            io.github.sekademi.spotufi.ui.components.PitchGuideMeter(
                onClose = { showPitchGuide = false }
            )
        }

        KaraokeControlSheet(
            visible = showKaraokeSheet,
            onDismiss = { showKaraokeSheet = false },
        )

        if (showQuoteCardDialog && state is LyricsViewModel.State.Loaded) {
            val lyricsData = (state as LyricsViewModel.State.Loaded).lyrics
            io.github.sekademi.spotufi.ui.components.LyricsQuoteCardDialog(
                lyrics = lyricsData,
                title = title,
                artist = artist,
                coverUrl = coverUrl,
                accentColor = accentColor,
                onDismiss = { showQuoteCardDialog = false },
            )
        }

        when (val s = state) {
            is LyricsViewModel.State.Loading ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            is LyricsViewModel.State.NotFound ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Couldn't find lyrics for this track", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
                }
            is LyricsViewModel.State.Loaded -> {
                val lyrics = s.lyrics
                val syllableLines = remember(lyrics) { io.github.sekademi.spotufi.data.lyrics.SyllableLyricsParser.parse(lyrics) }
                val activeIndex = activeIndexFor(lyrics, positionMs)
                val listState = rememberLazyListState()
                LaunchedEffect(activeIndex) {
                    if (activeIndex >= 0) {
                        listState.animateScrollToItem(activeIndex.coerceAtLeast(0), scrollOffset = -260)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp, 16.dp, 24.dp, 160.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    itemsIndexed(syllableLines) { index, line ->
                        SyllableLyricLineText(
                            line = line,
                            isActive = index == activeIndex,
                            synced = lyrics.synced,
                            positionMs = positionMs,
                            showTransliteration = showTransliteration,
                            fontSize = 24.sp,
                            onTap = if (lyrics.synced) ({ jumpTo(line.startMs) }) else null,
                        )
                    }
                }
            }
        }
    }
}

/** How many lines the inline lyrics card previews before "Show lyrics". */
private const val PREVIEW_LINE_COUNT = 5

/**
 * Inline lyrics card shown in the Now Playing scroll (no full-screen chrome).
 * Spotify-style *preview*: only a handful of lines (following the active synced
 * line) plus a "Show lyrics" button that opens the full-screen lyrics view.
 */
@Composable
fun InlineLyrics(
    title: String,
    artist: String,
    album: String,
    accentColor: Color,
    onExpand: () -> Unit,
) {
    val vm: LyricsViewModel = hiltViewModel()
    LaunchedEffect(title, artist) {
        val durationSec = (SongPlayer.getDuration() / 1000).toInt()
        vm.load(title, artist, album, durationSec)
    }
    val state by vm.state.collectAsState()
    val positionMs by rememberPlaybackPositionMs()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 8.dp, 16.dp, 40.dp)
            .background(
                Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.55f), accentColor.copy(alpha = 0.18f))),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onExpand() }
            .padding(20.dp)
    ) {
        Text("Lyrics preview", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(12.dp))

        when (val s = state) {
            is LyricsViewModel.State.Loading ->
                Text("Loading lyrics…", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
            is LyricsViewModel.State.NotFound ->
                Text("No lyrics found for this track", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
            is LyricsViewModel.State.Loaded -> {
                val lyrics = s.lyrics
                val activeIndex = activeIndexFor(lyrics, positionMs)
                // Preview window: keep the active synced line in view; plain
                // lyrics just show the first few lines.
                val windowStart =
                    if (lyrics.synced) activeIndex.coerceIn(0, (lyrics.lines.size - PREVIEW_LINE_COUNT).coerceAtLeast(0))
                    else 0
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    lyrics.lines.drop(windowStart).take(PREVIEW_LINE_COUNT).forEachIndexed { i, line ->
                        LyricLineText(
                            text = line.text,
                            isActive = windowStart + i == activeIndex,
                            synced = lyrics.synced,
                            fontSize = 22.sp,
                            onTap = if (lyrics.synced) ({ jumpTo(line.timeMs) }) else null,
                        )
                    }
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(18.dp))
                Box(
                    modifier = Modifier
                        .background(Color.White, shape = androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onExpand() }
                        .padding(16.dp, 8.dp)
                ) {
                    Text("Show lyrics", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun LyricLineText(
    text: String,
    isActive: Boolean,
    synced: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onTap: (() -> Unit)?,
) {
    if (text.isBlank()) {
        Box(modifier = Modifier.size(1.dp))
        return
    }
    val target = when {
        !synced -> Color.White.copy(alpha = 0.9f)
        isActive -> Color.White
        else -> Color.White.copy(alpha = 0.45f)
    }
    val color by animateColorAsState(targetValue = target, label = "lyricColor")
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        modifier = if (onTap != null) Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { onTap() } else Modifier,
    )
}

@Composable
private fun SyllableLyricLineText(
    line: io.github.sekademi.spotufi.data.lyrics.SyllableLine,
    isActive: Boolean,
    synced: Boolean,
    positionMs: Long,
    showTransliteration: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onTap: (() -> Unit)?,
) {
    if (line.originalText.isBlank()) {
        Box(modifier = Modifier.size(1.dp))
        return
    }

    Column(
        modifier = if (onTap != null) Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { onTap() } else Modifier
    ) {
        if (!isActive || !synced || line.words.isEmpty()) {
            val target = when {
                !synced -> Color.White.copy(alpha = 0.9f)
                isActive -> Color.White
                else -> Color.White.copy(alpha = 0.45f)
            }
            val color by animateColorAsState(targetValue = target, label = "syllableLineColor")
            Text(
                text = line.originalText,
                color = color,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                lineHeight = (fontSize.value * 1.3f).sp,
            )
        } else {
            val annotated = androidx.compose.ui.text.buildAnnotatedString {
                for (i in line.words.indices) {
                    val w = line.words[i]
                    val prog = w.progressAt(positionMs)
                    val spanColor = when {
                        prog >= 1f -> Color.White
                        prog > 0f -> Color(0xFF1ED760) // Glowing active singing highlight
                        else -> Color.White.copy(alpha = 0.5f)
                    }
                    pushStyle(
                        androidx.compose.ui.text.SpanStyle(
                            color = spanColor,
                            fontWeight = if (prog > 0f) FontWeight.ExtraBold else FontWeight.Bold
                        )
                    )
                    append(w.text)
                    pop()
                    if (i < line.words.size - 1) {
                        append(" ")
                    }
                }
            }
            Text(
                text = annotated,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                lineHeight = (fontSize.value * 1.3f).sp,
            )
        }

        if (showTransliteration && !line.transliteration.isNullOrBlank()) {
            Text(
                text = line.transliteration,
                color = if (isActive) Color(0xFF1ED760).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.45f),
                fontSize = (fontSize.value * 0.62f).sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

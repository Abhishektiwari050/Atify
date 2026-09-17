package io.github.sekademi.spotufi.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import io.github.sekademi.spotufi.data.entity.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Spotify-style Lyrics Quote Card Generator.
 * Allows users to select 1–4 lyric lines and export an aesthetic, high-res shareable image card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsQuoteCardDialog(
    lyrics: Lyrics,
    title: String,
    artist: String,
    coverUrl: String,
    accentColor: Color,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedIndices = remember { mutableStateListOf<Int>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141416),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Share Lyrics Card",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Select up to 4 lines to feature on your card",
                        color = Color.Gray,
                        fontSize = 13.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2A2A))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Card Preview
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(accentColor, Color(0xFF121216)),
                            )
                        )
                        .padding(16.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Song header
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = "Cover",
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = artist,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        // Selected quote lines
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (selectedIndices.isEmpty()) {
                                Text(
                                    text = "“ Tap any lines below to create your quote card ”",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            } else {
                                selectedIndices.sorted().take(4).forEach { idx ->
                                    val line = lyrics.lines.getOrNull(idx)?.text.orEmpty()
                                    if (line.isNotBlank()) {
                                        Text(
                                            text = line,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }

                        // Watermark branding
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Atify • Lossless Audio",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${selectedIndices.size}/4 lines",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Selectable Lyrics List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(lyrics.lines) { index, line ->
                    if (line.text.isNotBlank()) {
                        val isSelected = index in selectedIndices
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) accentColor.copy(alpha = 0.22f) else Color(0xFF1E1E24))
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.dp,
                                    color = if (isSelected) accentColor else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable {
                                    if (isSelected) {
                                        selectedIndices.remove(index)
                                    } else if (selectedIndices.size < 4) {
                                        selectedIndices.add(index)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = line.text,
                                color = if (isSelected) Color.White else Color.LightGray,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(accentColor),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Button
            Button(
                onClick = {
                    if (selectedIndices.isEmpty()) return@Button
                    scope.launch {
                        val linesToQuote = selectedIndices.sorted().take(4).map { lyrics.lines[it].text }
                        shareQuoteCard(context, linesToQuote, title, artist, coverUrl, accentColor.toArgb())
                        onDismiss()
                    }
                },
                enabled = selectedIndices.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    disabledContainerColor = Color(0xFF2E2E36),
                ),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share Image Card", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

/**
 * Generates a high-resolution 1080x1350 bitmap card and dispatches Android share intent.
 */
private suspend fun shareQuoteCard(
    context: Context,
    lines: List<String>,
    title: String,
    artist: String,
    coverUrl: String,
    accentArgb: Int,
) = withContext(Dispatchers.IO) {
    try {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Background Gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(accentArgb, 0xFF181820.toInt(), 0xFF0D0D10.toInt()),
                floatArrayOf(0f, 0.65f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Fetch Album Artwork Thumbnail
        var coverBitmap: Bitmap? = null
        if (coverUrl.isNotBlank()) {
            runCatching {
                val req = ImageRequest.Builder(context).data(coverUrl).size(240, 240).build()
                val result = context.imageLoader.execute(req)
                if (result is SuccessResult) {
                    coverBitmap = result.image.toBitmap()
                }
            }
        }

        val padding = 90f
        var currentY = 110f

        // 3. Draw Track Header
        if (coverBitmap != null) {
            val thumbSize = 130f
            val thumbRect = RectF(padding, currentY, padding + thumbSize, currentY + thumbSize)
            val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawRoundRect(thumbRect, 16f, 16f, thumbPaint)
            val scaledCover = Bitmap.createScaledBitmap(coverBitmap, thumbSize.toInt(), thumbSize.toInt(), true)
            canvas.drawBitmap(scaledCover, padding, currentY, thumbPaint)
        }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#CCCCCC")
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val textStartX = padding + 150f
        canvas.drawText(title.take(32), textStartX, currentY + 55f, titlePaint)
        canvas.drawText(artist.take(38), textStartX, currentY + 105f, artistPaint)

        currentY += 240f

        // 4. Large Quote Mark
        val quoteMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            alpha = 70
            textSize = 140f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText("“", padding, currentY, quoteMarkPaint)
        currentY += 40f

        // 5. Quoted Lyric Lines
        val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 52f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        for (line in lines) {
            val layout = StaticLayout.Builder.obtain(
                line, 0, line.length, lyricPaint, (width - padding * 2).toInt()
            ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(14f, 1f)
                .build()

            canvas.save()
            canvas.translate(padding, currentY)
            layout.draw(canvas)
            canvas.restore()
            currentY += layout.height + 40f
        }

        // 6. Bottom Branding
        val bottomY = height - 100f
        val linePaint = Paint().apply {
            color = android.graphics.Color.parseColor("#33FFFFFF")
            strokeWidth = 3f
        }
        canvas.drawLine(padding, bottomY - 30f, width - padding, bottomY - 30f, linePaint)

        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#EEEEEE")
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Atify • Lossless Audio", padding, bottomY + 10f, brandPaint)

        // Save to cacheDir
        val cacheDir = File(context.cacheDir, "quote_cards").apply { mkdirs() }
        val imageFile = File(cacheDir, "atify_lyrics_${System.currentTimeMillis()}.png")
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile,
        )

        withContext(Dispatchers.Main) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "“${lines.firstOrNull() ?: ""}” — $title by $artist (via Atify)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Lyrics Card"))
        }
    } catch (e: Exception) {
        android.util.Log.e("LyricsQuoteCard", "Failed to render/share quote card", e)
    }
}

package io.github.sekademi.spotufi.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.sekademi.spotufi.data.porter.UniversalPlaylistPorter
import kotlinx.coroutines.launch

/**
 * Dialog for importing playlists from Apple Music, YouTube Music, and Deezer URLs.
 */
@Composable
fun UniversalPorterDialog(
    onDismiss: () -> Unit,
    onSuccess: (playlistId: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var urlText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E26), RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = "Universal Playlist Porter",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Import any public playlist into your local library with one tap.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Supported source badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("Apple Music", "YouTube Music", "Deezer").forEach { source ->
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(source, color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        errorMessage = null
                    },
                    label = { Text("Paste playlist link", color = Color.White.copy(alpha = 0.6f)) },
                    placeholder = { Text("https://music.apple.com/... or https://youtube.com/...", color = Color.Gray, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF1ED760),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    ),
                    trailingIcon = {
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                    if (!clip.isNullOrBlank()) {
                                        urlText = clip.trim()
                                        errorMessage = null
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Paste", color = Color(0xFF1ED760), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Cancel",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onDismiss() }
                            .padding(12.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                if (urlText.isNotBlank() && !isLoading) Color(0xFF1ED760) else Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(50)
                            )
                            .clickable(
                                enabled = urlText.isNotBlank() && !isLoading,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    val result = UniversalPlaylistPorter.importPlaylistFromUrl(context, urlText)
                                    isLoading = false
                                    result.onSuccess { playlist ->
                                        onSuccess(playlist.id)
                                    }.onFailure { err ->
                                        errorMessage = err.localizedMessage ?: "Failed to import playlist."
                                    }
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text("Import", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

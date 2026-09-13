package io.github.sekademi.spotufi.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sekademi.spotufi.audio.EqualizerEngine
import io.github.sekademi.spotufi.data.entity.AudioStreamDetails
import io.github.sekademi.spotufi.data.preferences.getEqualizerPreset
import io.github.sekademi.spotufi.data.preferences.isEqualizerEnabled
import io.github.sekademi.spotufi.data.preferences.isVolumeNormalizationEnabled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDetailsSheet(
    details: AudioStreamDetails,
    context: Context,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF18181A),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text(
                        text = "Audio Stream Quality",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Live playback codec & hardware sink details",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                val badgeColor = when {
                    details.source.startsWith("Lossless") -> Color(0xFFFFC862)
                    details.source == "Downloaded" -> Color(0xFF9C9C9C)
                    details.source == "Spotify" -> Color(0xFF1ED760)
                    else -> Color(0xFF3DABFF)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = if (details.source == "YouTube") "Streamed" else details.source,
                        color = badgeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF222226))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                DetailRow("Audio Codec", details.displayCodec)
                HorizontalDivider(color = Color(0xFF2E2E33), thickness = 0.8.dp)
                DetailRow("Estimated Bitrate", details.formattedBitrate)
                HorizontalDivider(color = Color(0xFF2E2E33), thickness = 0.8.dp)
                DetailRow("Sample Rate", details.formattedSampleRate)
                HorizontalDivider(color = Color(0xFF2E2E33), thickness = 0.8.dp)
                DetailRow("Channels", details.formattedChannels)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "DSP & Output Engine",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 6.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF222226))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val offloadText = if (details.isOffloadActive) "Enabled (Low-Power DSP)" else "Disabled"
                DetailRow("Hardware Audio Offload", offloadText)
                HorizontalDivider(color = Color(0xFF2E2E33), thickness = 0.8.dp)
                val eqActive = isEqualizerEnabled(context)
                val eqText = if (eqActive) "Active (${getEqualizerPreset(context)})" else "Off"
                DetailRow("Graphic Equalizer", eqText)
                HorizontalDivider(color = Color(0xFF2E2E33), thickness = 0.8.dp)
                val normText = if (isVolumeNormalizationEnabled(context)) "Enabled (LoudnessEnhancer)" else "Off"
                DetailRow("Volume Normalization", normText)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color.LightGray,
            fontSize = 14.sp,
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

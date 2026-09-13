package io.github.sekademi.spotufi.ui.screens

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.sekademi.spotufi.audio.EqualizerEngine
import io.github.sekademi.spotufi.data.preferences.getEqualizerPreset
import io.github.sekademi.spotufi.ui.theme.AppBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(navController: NavController) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(EqualizerEngine.isEnabled(context)) }
    var selectedPreset by remember { mutableStateOf(getEqualizerPreset(context)) }

    val bands = remember { EqualizerEngine.getBands(context) }
    val bandLevels = remember {
        mutableStateMapOf<Int, Int>().apply {
            bands.forEach { put(it.index, it.levelMillibels) }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Equalizer",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground,
                ),
            )
        },
        containerColor = AppBackground,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // Master Enable Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E1E22))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Equalizer Audio Processing",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (isEnabled) "Active on music output" else "Bypassed (flat response)",
                        color = if (isEnabled) Color(0xFF1ED760) else Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        isEnabled = checked
                        EqualizerEngine.setEnabled(context, checked)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF1ED760),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color(0xFF2E2E33),
                    ),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Presets row
            Text(
                text = "Presets",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val allPresets = listOf("Flat", "Bass Boost", "Treble Boost", "Vocal Boost", "Rock", "Pop", "Electronic", "Jazz", "Acoustic", "Custom")
                allPresets.forEach { preset ->
                    val isSelected = selectedPreset == preset
                    val chipBg by animateColorAsState(
                        if (isSelected) Color(0xFF1ED760) else Color(0xFF222226),
                        label = "chipBg",
                    )
                    val chipTextColor by animateColorAsState(
                        if (isSelected) Color.Black else Color.LightGray,
                        label = "chipTextColor",
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(chipBg)
                            .clickable {
                                selectedPreset = preset
                                EqualizerEngine.applyPreset(context, preset)
                                val updated = EqualizerEngine.getBands(context)
                                updated.forEach { bandLevels[it.index] = it.levelMillibels }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = preset,
                            color = chipTextColor,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Frequency Response Curve Canvas
            Text(
                text = "Frequency Response",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF19191C))
                    .padding(16.dp),
            ) {
                val levels = bands.map { bandLevels[it.index] ?: 0 }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val midY = h / 2f

                    // Draw center 0dB grid line
                    drawLine(
                        color = Color(0xFF2E2E33),
                        start = Offset(0f, midY),
                        end = Offset(w, midY),
                        strokeWidth = 1.dp.toPx(),
                    )

                    if (levels.isNotEmpty()) {
                        val stepX = w / (levels.size - 1).coerceAtLeast(1)
                        val points = levels.mapIndexed { i, mb ->
                            val normalized = (mb / 1500f).coerceIn(-1f, 1f)
                            val y = midY - (normalized * (h * 0.42f))
                            Offset(i * stepX, y)
                        }

                        val path = Path().apply {
                            moveTo(points[0].x, points[0].y)
                            for (i in 0 until points.size - 1) {
                                val p0 = points[i]
                                val p1 = points[i + 1]
                                val cx = (p0.x + p1.x) / 2f
                                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                            }
                        }

                        // Gradient fill under curve
                        val fillPath = Path().apply {
                            addPath(path)
                            lineTo(w, h)
                            lineTo(0f, h)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1ED760).copy(alpha = if (isEnabled) 0.25f else 0.05f),
                                    Color.Transparent,
                                ),
                            ),
                        )

                        // Stroke curve line
                        drawPath(
                            path = path,
                            color = if (isEnabled) Color(0xFF1ED760) else Color.Gray,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                        )

                        // Data points
                        points.forEach { pt ->
                            drawCircle(
                                color = if (isEnabled) Color(0xFF1ED760) else Color.Gray,
                                radius = 4.dp.toPx(),
                                center = pt,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Band Sliders
            Text(
                text = "Band Adjustment",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E1E22))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                bands.forEach { band ->
                    val currentMb = bandLevels[band.index] ?: 0
                    val currentDb = currentMb / 100

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = band.displayFrequency,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (currentDb > 0) "+$currentDb dB" else "$currentDb dB",
                                color = if (currentDb != 0 && isEnabled) Color(0xFF1ED760) else Color.Gray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        Slider(
                            value = currentMb.toFloat(),
                            onValueChange = { newVal ->
                                val intVal = newVal.toInt()
                                bandLevels[band.index] = intVal
                                selectedPreset = "Custom"
                                EqualizerEngine.setBandLevel(context, band.index, intVal)
                            },
                            valueRange = band.minMillibels.toFloat()..band.maxMillibels.toFloat(),
                            enabled = isEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF1ED760),
                                activeTrackColor = Color(0xFF1ED760),
                                inactiveTrackColor = Color(0xFF2E2E33),
                                disabledThumbColor = Color.DarkGray,
                                disabledActiveTrackColor = Color.DarkGray,
                                disabledInactiveTrackColor = Color(0xFF2E2E33),
                            ),
                        )
                    }
                }
            }
        }
    }
}

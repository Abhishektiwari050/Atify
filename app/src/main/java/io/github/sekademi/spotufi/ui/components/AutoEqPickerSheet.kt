package io.github.sekademi.spotufi.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sekademi.spotufi.R
import io.github.sekademi.spotufi.audio.AutoEqEngine
import io.github.sekademi.spotufi.audio.AutoEqProfile
import io.github.sekademi.spotufi.ui.theme.AppPalette

/**
 * Bottom sheet for searching, selecting, and calibrating headphones using AutoEQ curves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoEqPickerSheet(
    context: Context,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    val allProfiles = remember { AutoEqEngine.getProfiles(context) }
    var activeProfile by remember { mutableStateOf(AutoEqEngine.getActiveProfile(context)) }
    var isEnabled by remember { mutableStateOf(AutoEqEngine.isEnabled(context)) }
    var autoDetect by remember { mutableStateOf(AutoEqEngine.isAutoDetectEnabled(context)) }

    val filteredProfiles = remember(searchQuery, allProfiles) {
        if (searchQuery.isBlank()) allProfiles
        else allProfiles.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.type.contains(searchQuery, ignoreCase = true) ||
            it.aliases.any { a -> a.contains(searchQuery, ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16161C),
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "AutoEQ Headphone Studio",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Harman & diffuse-field target calibration",
                        color = Color.Gray,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = {
                        isEnabled = it
                        AutoEqEngine.setEnabled(context, it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(AppPalette.toArgb()),
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bluetooth Auto-Detect switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF22222C))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-detect on Bluetooth connect", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Applies profile matching your device name", color = Color.Gray, fontSize = 11.sp)
                }
                Switch(
                    checked = autoDetect,
                    onCheckedChange = {
                        autoDetect = it
                        AutoEqEngine.setAutoDetectEnabled(context, it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(AppPalette.toArgb()),
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF22222C))
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search_big),
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search Sony, AirPods, Sennheiser, Bose…", color = Color.Gray, fontSize = 13.sp) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color(AppPalette.toArgb()),
                    ),
                    textStyle = TextStyle(fontSize = 13.sp),
                    modifier = Modifier.weight(1f)
                )
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = Color.Gray,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { searchQuery = "" }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Profiles list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
            ) {
                // Clear selection / None
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                activeProfile = null
                                AutoEqEngine.setActiveProfile(context, null)
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("None (Bypass AutoEQ)", color = if (activeProfile == null) Color(AppPalette.toArgb()) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Standard uncalibrated sound profile", color = Color.Gray, fontSize = 11.sp)
                        }
                        if (activeProfile == null) {
                            Icon(Icons.Default.Check, contentDescription = "Active", tint = Color(AppPalette.toArgb()), modifier = Modifier.size(20.dp))
                        }
                    }
                    HorizontalDivider(color = Color(0xFF262632))
                }

                items(filteredProfiles, key = { it.name }) { profile ->
                    val isSelected = activeProfile?.name == profile.name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                activeProfile = profile
                                AutoEqEngine.setActiveProfile(context, profile)
                                AutoEqEngine.setEnabled(context, true)
                                isEnabled = true
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = profile.name,
                                    color = if (isSelected) Color(AppPalette.toArgb()) else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF2D2D3A))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(profile.type, color = Color(0xFFB0B0C8), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${profile.bands.size} parametric bands • Preamp: ${profile.preampDb} dB",
                                color = Color.Gray,
                                fontSize = 11.sp,
                            )
                        }
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = "Active", tint = Color(AppPalette.toArgb()), modifier = Modifier.size(20.dp))
                        }
                    }
                    HorizontalDivider(color = Color(0xFF262632))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

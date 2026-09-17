package io.github.sekademi.spotufi.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.sekademi.spotufi.R
import io.github.sekademi.spotufi.connect.AtifyConnectClient
import io.github.sekademi.spotufi.connect.AtifyConnectServer
import io.github.sekademi.spotufi.data.preferences.isWebRemoteEnabled
import io.github.sekademi.spotufi.data.preferences.setWebRemoteEnabled
import io.github.sekademi.spotufi.ui.theme.AppBackground
import io.github.sekademi.spotufi.ui.theme.AppPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtifyConnectScreen(navController: NavController) {
    val context = LocalContext.current
    var isServerRunning by remember { mutableStateOf(AtifyConnectServer.isRunning) }
    var localIp by remember { mutableStateOf(AtifyConnectServer.getLocalIpAddress()) }
    val serverUrl = "http://$localIp:${AtifyConnectServer.PORT}"

    var joinIp by remember { mutableStateOf("") }
    var isJoined by remember { mutableStateOf(AtifyConnectClient.isJoined) }
    var isConnecting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(16.dp, 0.dp)
                            .clickable { navController.navigateUp() }
                    )
                },
                title = {
                    Text(
                        text = "Atify Connect",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(AppBackground.toArgb()),
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(AppBackground.toArgb()))
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // ── Section 1: Local Web Remote ──
            Text(
                text = "Local Web Remote",
                color = Color(AppPalette.toArgb()),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF181822))
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Web Remote Server",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isServerRunning) "Broadcasting on your local Wi-Fi" else "Control playback from PC, Mac, or tablet",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = isServerRunning,
                    onCheckedChange = { start ->
                        isServerRunning = start
                        setWebRemoteEnabled(context, start)
                        if (start) {
                            AtifyConnectServer.start(context)
                            localIp = AtifyConnectServer.getLocalIpAddress()
                        } else {
                            AtifyConnectServer.stop()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(AppPalette.toArgb()),
                    )
                )
            }

            if (isServerRunning) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E2620))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Open this link in any browser on your Wi-Fi:",
                        color = Color(0xFFC0E0C8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = serverUrl,
                        color = Color(AppPalette.toArgb()),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(AppPalette.toArgb()))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Atify URL", serverUrl))
                                    Toast.makeText(context, "Copied $serverUrl", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Copy Link", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2A3A2C))
                                .clickable {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl)))
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Open in Browser", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // ── Section 2: Party Sync ("Listen Together") ──
            Text(
                text = "Party Sync (Listen Together)",
                color = Color(AppPalette.toArgb()),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF181822))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Turn multiple phones into a synchronized multi-speaker array on the same Wi-Fi.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF262632))
                Spacer(modifier = Modifier.height(14.dp))

                if (isJoined) {
                    Text(
                        text = "Joined Party with host ${AtifyConnectClient.hostAddress}",
                        color = Color(AppPalette.toArgb()),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF402020))
                            .clickable {
                                AtifyConnectClient.leaveParty()
                                isJoined = false
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("Leave Party", color = Color(0xFFFF6666), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = "Join a Friend's Party:",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = joinIp,
                            onValueChange = { joinIp = it },
                            placeholder = { Text("e.g. 192.168.1.50", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(AppPalette.toArgb()),
                                unfocusedBorderColor = Color(0xFF333340),
                                cursorColor = Color(AppPalette.toArgb()),
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (joinIp.isNotBlank() && !isConnecting) Color(AppPalette.toArgb()) else Color(0xFF333340))
                                .clickable(enabled = joinIp.isNotBlank() && !isConnecting) {
                                    isConnecting = true
                                    AtifyConnectClient.joinParty(context, joinIp) { success ->
                                        isConnecting = false
                                        isJoined = success
                                        if (success) {
                                            Toast.makeText(context, "Synchronized with Party host!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Could not reach host. Check Wi-Fi IP.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Text(if (isConnecting) "Joining…" else "Join", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

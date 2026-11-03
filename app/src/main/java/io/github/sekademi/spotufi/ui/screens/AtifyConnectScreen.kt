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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.sekademi.spotufi.connect.AtifyConnectClient
import io.github.sekademi.spotufi.connect.AtifyConnectServer
import io.github.sekademi.spotufi.connect.AtifyPartyManager
import io.github.sekademi.spotufi.connect.AtifyPartyManager.PartyRole
import io.github.sekademi.spotufi.data.preferences.isWebRemoteEnabled
import io.github.sekademi.spotufi.data.preferences.setWebRemoteEnabled
import io.github.sekademi.spotufi.ui.theme.AppBackground
import io.github.sekademi.spotufi.ui.theme.AppPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtifyConnectScreen(navController: NavController) {
    val context = LocalContext.current

    // Online WAN Party State
    val partyRole by AtifyPartyManager.partyRole.collectAsState()
    val formattedPartyCode by AtifyPartyManager.formattedCode.collectAsState()
    val statusMessage by AtifyPartyManager.statusMessage.collectAsState()
    val syncedTitle by AtifyPartyManager.syncedTitle.collectAsState()
    val syncedSinger by AtifyPartyManager.syncedSinger.collectAsState()

    var inputPartyCode by remember { mutableStateOf("") }
    var isJoiningParty by remember { mutableStateOf(false) }

    // Local Web Remote State
    var isServerRunning by remember { mutableStateOf(AtifyConnectServer.isRunning) }
    var localIp by remember { mutableStateOf(AtifyConnectServer.getLocalIpAddress()) }
    val serverUrl = "http://$localIp:${AtifyConnectServer.PORT}"

    // Local LAN Wi-Fi Multi-Phone Sync State
    var joinIp by remember { mutableStateOf("") }
    var isJoinedLan by remember { mutableStateOf(AtifyConnectClient.isJoined) }
    var isConnectingLan by remember { mutableStateOf(false) }

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
            // ── Section 1: Online Party (Listen Together Anywhere) ──
            Text(
                text = "LISTEN TOGETHER (ONLINE PARTY)",
                color = Color(AppPalette.toArgb()),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Listen in real-time sync with friends anywhere on Earth over mobile data (4G/5G) or Wi-Fi.",
                color = Color(0xFFAAAAAA),
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (partyRole) {
                PartyRole.HOST -> {
                    // Host Active Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF14241B))
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "HOSTING LIVE PARTY",
                                color = Color(AppPalette.toArgb()),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(AppPalette.toArgb()))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("LIVE", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = formattedPartyCode,
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            text = "Share this code with friends to let them join your live stream.",
                            color = Color(0xFFB0D0B8),
                            fontSize = 12.sp,
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(AppPalette.toArgb()))
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Party Code", formattedPartyCode))
                                        Toast.makeText(context, "Copied code $formattedPartyCode", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Copy Code", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF233526))
                                    .clickable {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Join my listening party on Atify! Party Code: $formattedPartyCode"
                                            )
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Party Code"))
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text("Share Invite", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF381C1C))
                                .clickable {
                                    AtifyPartyManager.leaveOrEndParty()
                                    Toast.makeText(context, "Party ended", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("End Party", color = Color(0xFFFF6B6B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                PartyRole.LISTENER -> {
                    // Listener Connected Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF16202A))
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "CONNECTED TO PARTY",
                            color = Color(0xFF64B5F6),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formattedPartyCode,
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (syncedTitle.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF223040))
                                    .padding(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = syncedTitle,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = syncedSinger,
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                    )
                                }
                                Text(
                                    text = "In Sync",
                                    color = Color(AppPalette.toArgb()),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF381C1C))
                                .clickable {
                                    AtifyPartyManager.leaveOrEndParty()
                                    Toast.makeText(context, "Left party", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Leave Party", color = Color(0xFFFF6B6B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                PartyRole.NONE -> {
                    // Offline / Join or Host Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF181822))
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "Host a Session",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Get a 6-digit party code and stream your music to friends anywhere.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(AppPalette.toArgb()))
                                .clickable {
                                    val code = AtifyPartyManager.createParty(context)
                                    Toast.makeText(context, "Party Room created: $code", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Start Online Party",
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF262632))
                            Text(
                                text = "  OR JOIN A FRIEND  ",
                                color = Color(0xFF666677),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF262632))
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Enter 6-Digit Party Code:",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = inputPartyCode,
                                onValueChange = { inputPartyCode = it },
                                placeholder = { Text("e.g. 482-910", color = Color.Gray) },
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
                                    .background(
                                        if (inputPartyCode.isNotBlank() && !isJoiningParty) Color(AppPalette.toArgb()) else Color(0xFF333340)
                                    )
                                    .clickable(enabled = inputPartyCode.isNotBlank() && !isJoiningParty) {
                                        isJoiningParty = true
                                        AtifyPartyManager.joinParty(context, inputPartyCode) { success, error ->
                                            isJoiningParty = false
                                            if (success) {
                                                Toast.makeText(context, "Joined Online Party!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, error ?: "Failed to join party", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    .padding(horizontal = 18.dp, vertical = 14.dp)
                            ) {
                                if (isJoiningParty) {
                                    CircularProgressIndicator(
                                        color = Color.Black,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Text("Join", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Section 2: Local Web Remote ──
            Text(
                text = "LOCAL WEB REMOTE (BROWSER)",
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isServerRunning) "Broadcasting on local Wi-Fi" else "Control playback from PC, Mac, or tablet browser",
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
                Spacer(modifier = Modifier.height(10.dp))
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

            Spacer(modifier = Modifier.height(28.dp))

            // ── Section 3: Local Wi-Fi Multi-Phone Sync ──
            Text(
                text = "LOCAL WI-FI MULTI-PHONE SYNC",
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
                    text = "Direct router IP socket sync for phones on the same Wi-Fi router.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (isJoinedLan) {
                    Text(
                        text = "Joined LAN Host ${AtifyConnectClient.hostAddress}",
                        color = Color(AppPalette.toArgb()),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF402020))
                            .clickable {
                                AtifyConnectClient.leaveParty()
                                isJoinedLan = false
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Leave LAN Party", color = Color(0xFFFF6666), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
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
                                .background(if (joinIp.isNotBlank() && !isConnectingLan) Color(AppPalette.toArgb()) else Color(0xFF333340))
                                .clickable(enabled = joinIp.isNotBlank() && !isConnectingLan) {
                                    isConnectingLan = true
                                    AtifyConnectClient.joinParty(context, joinIp) { success ->
                                        isConnectingLan = false
                                        isJoinedLan = success
                                        if (success) {
                                            Toast.makeText(context, "Synchronized with LAN host!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Could not reach LAN host IP.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Text(if (isConnectingLan) "Joining…" else "Connect", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(130.dp))
        }
    }
}

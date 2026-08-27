package io.github.sekademi.spotufi.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.SpotifyAuth
import io.github.sekademi.spotufi.R
import io.github.sekademi.spotufi.data.api.SpotifySession
import io.github.sekademi.spotufi.ui.navigation.Routes
import io.github.sekademi.spotufi.ui.theme.AtifyDark
import io.github.sekademi.spotufi.ui.theme.AtifySage
import io.github.sekademi.spotufi.ui.theme.AtifySand
import io.github.sekademi.spotufi.ui.theme.AtifyRust
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SpotifyLogin"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Web Login, 1 = Cookie (sp_dc)

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    var cookieInput by remember { mutableStateOf("") }
    var cookieError by remember { mutableStateOf<String?>(null) }

    var loadProgress by remember { mutableFloatStateOf(0.1f) }
    var isWebLoading by remember { mutableStateOf(true) }
    var webError by remember { mutableStateOf<String?>(null) }

    val tokenFetchStarted = remember { AtomicBoolean(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val navigateToHome: () -> Unit = {
        io.github.sekademi.spotufi.di.SpotifyWebPlayer.refreshLogin(context)
        navController.navigate(Routes.Home.route) {
            popUpTo(Routes.Login.route) { inclusive = true }
        }
    }

    // Auto-detect sp_dc in CookieManager every 500ms
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            if (tokenFetchStarted.get()) continue
            val spDc = extractSpotifyDcCookie()
            if (!spDc.isNullOrBlank() && tokenFetchStarted.compareAndSet(false, true)) {
                Log.d(TAG, "sp_dc cookie auto-detected from session!")
                finishLogin(
                    webViewRef, context as Activity, scope,
                    spDc = spDc,
                    setProcessing = { isProcessing = it },
                    setStatus = { statusMessage = it },
                    setError = { hasError = it },
                    tokenFetchStarted = tokenFetchStarted,
                    onSuccess = navigateToHome,
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AtifyDark)
            .statusBarsPadding()
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color(0xFF18251F))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (navController.previousBackStackEntry != null) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_player_back),
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }

            Text(
                text = if (isProcessing) statusMessage.ifBlank { "Authenticating…" } else "Sign in to Atify",
                color = if (hasError) Color(0xFFFF5252) else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )

            if (selectedTab == 0) {
                IconButton(onClick = {
                    webError = null
                    isWebLoading = true
                    loadProgress = 0.1f
                    webViewRef?.reload()
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload",
                        tint = Color.White
                    )
                }
            }
        }

        // Tab Navigation
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF18251F),
            contentColor = Color.White,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = AtifySage,
                    height = 3.dp
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        "Web Login",
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == 0) Color.White else Color.Gray,
                        fontSize = 14.sp
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        "Cookie (sp_dc)",
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == 1) Color.White else Color.Gray,
                        fontSize = 14.sp
                    )
                }
            )
        }

        if (selectedTab == 0 && isWebLoading && loadProgress in 0.01f..0.99f) {
            LinearProgressIndicator(
                progress = { loadProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = AtifySage,
                trackColor = Color.Transparent
            )
        }

        // Content Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(AtifyDark)
        ) {
            if (selectedTab == 0) {
                // WEB LOGIN TAB
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)

                            WebView(ctx).apply {
                                webViewRef = this
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setBackgroundColor(android.graphics.Color.parseColor("#121212"))
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    setSupportMultipleWindows(false)
                                    javaScriptCanOpenWindowsAutomatically = true
                                    allowContentAccess = true
                                    allowFileAccess = true
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    // Use default system user agent so Google & Spotify accept the browser
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress / 100f
                                        if (newProgress >= 80) isWebLoading = false
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        Log.d(TAG, "Web starting: $url")
                                        webError = null
                                        isWebLoading = true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        Log.d(TAG, "Web finished: $url")
                                        isWebLoading = false
                                        loadProgress = 1f
                                        val spDc = extractSpotifyDcCookie()
                                        if (!spDc.isNullOrBlank() && tokenFetchStarted.compareAndSet(false, true)) {
                                            finishLogin(
                                                this@apply, context as Activity, scope,
                                                spDc = spDc,
                                                setProcessing = { isProcessing = it },
                                                setStatus = { statusMessage = it },
                                                setError = { hasError = it },
                                                tokenFetchStarted = tokenFetchStarted,
                                                onSuccess = navigateToHome,
                                            )
                                        }
                                    }

                                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                        if (request?.isForMainFrame == true) {
                                            Log.e(TAG, "Web error: ${error?.description}")
                                            webError = error?.description?.toString() ?: "Network error"
                                            isWebLoading = false
                                        }
                                    }

                                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                        handler?.proceed()
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        return false
                                    }
                                }

                                loadUrl("https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F")
                            }
                        }
                    )

                    // Floating "I have Logged In" Action Bar at Bottom of Web Tab
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xFF18251F).copy(alpha = 0.95f), Color(0xFF18251F))
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Logged in successfully?",
                                color = Color.White,
                                fontSize = 13.sp
                            )
                            Button(
                                onClick = {
                                    val spDc = extractSpotifyDcCookie()
                                    if (!spDc.isNullOrBlank()) {
                                        if (tokenFetchStarted.compareAndSet(false, true)) {
                                            finishLogin(
                                                webViewRef, context as Activity, scope,
                                                spDc = spDc,
                                                setProcessing = { isProcessing = it },
                                                setStatus = { statusMessage = it },
                                                setError = { hasError = it },
                                                tokenFetchStarted = tokenFetchStarted,
                                                onSuccess = navigateToHome,
                                            )
                                        }
                                    } else {
                                        Toast.makeText(context, "No active Spotify session found yet. Please complete login above or use Cookie tab.", Toast.LENGTH_LONG).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AtifySage),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Complete Sign In", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    if (webError != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(AtifyDark)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Unable to load web view", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(webError ?: "Network connection failed", color = Color.LightGray, fontSize = 13.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    webError = null
                                    isWebLoading = true
                                    webViewRef?.loadUrl("https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AtifySage)
                            ) {
                                Text("Retry Web View")
                            }
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { selectedTab = 1 }) {
                                Text("Switch to Cookie Login (100% Reliable)", color = AtifySand)
                            }
                        }
                    }
                }
            } else {
                // DIRECT COOKIE LOGIN TAB (Guaranteed 100% reliable)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(10.dp))

                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = AtifySage,
                        modifier = Modifier.size(44.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Instant Cookie Authentication",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Bypasses all browser restrictions, captchas, and bot detection.",
                        color = Color(0xFFD8C7A8),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(24.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2D26)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Spotify 'sp_dc' Cookie",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = cookieInput,
                                onValueChange = {
                                    cookieInput = it
                                    cookieError = null
                                },
                                placeholder = { Text("AQB... (Paste your sp_dc cookie)", color = Color.Gray, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = AtifySage,
                                    unfocusedBorderColor = Color(0xFF3B4F43),
                                    cursorColor = AtifySage
                                )
                            )

                            if (cookieError != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(cookieError!!, color = Color(0xFFFF5252), fontSize = 12.sp)
                            }

                            Spacer(Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = clipboard.primaryClip
                                        if (clip != null && clip.itemCount > 0) {
                                            val text = clip.getItemAt(0).text?.toString().orEmpty().trim()
                                            if (text.isNotBlank()) {
                                                cookieInput = text
                                                cookieError = null
                                            } else {
                                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AtifySand),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Paste Clipboard", fontSize = 13.sp)
                                }

                                Button(
                                    onClick = {
                                        val clean = cookieInput.trim()
                                        if (clean.isBlank()) {
                                            cookieError = "Please paste a valid sp_dc cookie"
                                            return@Button
                                        }
                                        if (tokenFetchStarted.compareAndSet(false, true)) {
                                            finishLogin(
                                                null, context as Activity, scope,
                                                spDc = clean,
                                                setProcessing = { isProcessing = it },
                                                setStatus = { statusMessage = it },
                                                setError = { hasError = it },
                                                tokenFetchStarted = tokenFetchStarted,
                                                onSuccess = navigateToHome,
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = AtifySage),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Sign In", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Helpful instructions accordion / card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF141F1A)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("How to get your sp_dc cookie in 30 seconds:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("1. Open open.spotify.com in your PC browser (Chrome / Edge / Firefox) and log in.", color = Color.LightGray, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("2. Press F12 → Application tab (or Storage in Firefox) → Cookies → https://open.spotify.com.", color = Color.LightGray, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("3. Click 'sp_dc', copy the long value starting with AQB..., and paste it above.", color = Color.LightGray, fontSize = 12.sp)
                            Spacer(Modifier.height(14.dp))

                            OutlinedButton(
                                onClick = {
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F"))
                                    context.startActivity(browserIntent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AtifySand)
                            ) {
                                Text("Open Spotify in External Chrome Browser", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Fullscreen Processing Overlay
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        CircularProgressIndicator(
                            color = AtifySage,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = statusMessage.ifBlank { "Connecting to your Spotify account…" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * Searches CookieManager across all known Spotify domains for the session cookie `sp_dc`.
 */
private fun extractSpotifyDcCookie(): String? {
    val cookieManager = CookieManager.getInstance() ?: return null
    val targetUrls = listOf(
        "https://accounts.spotify.com",
        "https://open.spotify.com",
        "https://spotify.com",
        "https://api.spotify.com",
        "https://.spotify.com"
    )

    for (url in targetUrls) {
        val cookies = cookieManager.getCookie(url) ?: continue
        for (item in cookies.split(";")) {
            val trimmed = item.trim()
            if (trimmed.startsWith("sp_dc=")) {
                val value = trimmed.substringAfter("sp_dc=").trim()
                if (value.isNotBlank()) {
                    return value
                }
            }
        }
    }
    return null
}

private fun finishLogin(
    view: WebView?,
    activity: Activity,
    scope: kotlinx.coroutines.CoroutineScope,
    spDc: String,
    setProcessing: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    setError: (Boolean) -> Unit,
    tokenFetchStarted: AtomicBoolean,
    onSuccess: () -> Unit,
) {
    if (spDc.isBlank()) {
        setProcessing(true)
        setError(true)
        setStatus("Invalid login cookie. Please try again.")
        tokenFetchStarted.set(false)
        return
    }

    setProcessing(true)
    setError(false)
    setStatus("Connecting to Spotify…")
    view?.stopLoading()

    scope.launch(Dispatchers.IO) {
        SpotifySession.setSpDc(activity, spDc)
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            val result = SpotifyAuth.fetchAccessToken(spDc)
            result.onSuccess { token ->
                Spotify.accessToken = token.accessToken
                withContext(Dispatchers.Main) { setStatus("Authenticated!") }
                delay(300)
                withContext(Dispatchers.Main) { onSuccess() }
                return@launch
            }.onFailure { e ->
                lastError = e
                Timber.e(e, "Spotify token fetch failed (attempt ${attempt + 1})")
                if (attempt < 2) delay(800)
            }
        }
        withContext(Dispatchers.Main) {
            setStatus("Login failed: ${lastError?.message ?: "unknown error"}")
            setError(true)
            setProcessing(false)
        }
        tokenFetchStarted.set(false)
    }
}

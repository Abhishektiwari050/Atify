package io.github.sekademi.spotufi.ui.screens

import android.app.Activity
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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.metrolist.spotify.CookieSanitizer
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.SpotifyAuth
import io.github.sekademi.spotufi.R
import io.github.sekademi.spotufi.data.api.SpotifySession
import io.github.sekademi.spotufi.ui.components.CustomTabsHelper
import io.github.sekademi.spotufi.ui.navigation.Routes
import io.github.sekademi.spotufi.ui.theme.AtifyDark
import io.github.sekademi.spotufi.ui.theme.AtifySage
import io.github.sekademi.spotufi.ui.theme.AtifySand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SpotifyLogin"
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
private const val LOGIN_URL =
    "https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

private val DESKTOP_HEADERS = mapOf(
    "Sec-CH-UA" to "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
    "Sec-CH-UA-Mobile" to "?0",
    "Sec-CH-UA-Platform" to "\"Windows\"",
    "Accept-Language" to "en-US,en;q=0.9",
    "Upgrade-Insecure-Requests" to "1"
)

private const val VIEWPORT_OVERRIDE_JS = """
    (function() {
        try {
            var meta = document.querySelector('meta[name="viewport"]');
            if (!meta) {
                meta = document.createElement('meta');
                meta.name = 'viewport';
                document.head.appendChild(meta);
            }
            meta.setAttribute('content', 'width=1280, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes');
        } catch(e) {}
    })();
"""

private const val ANTI_BOT_SPOOF_JS = """
    (function() {
        try {
            if (navigator.userAgentData) {
                Object.defineProperty(navigator, 'userAgentData', {
                    configurable: true,
                    get: function() {
                        return {
                            brands: [
                                {brand: 'Google Chrome', version: '131'},
                                {brand: 'Chromium', version: '131'},
                                {brand: 'Not_A Brand', version: '24'}
                            ],
                            mobile: false,
                            platform: 'Windows'
                        };
                    }
                });
            }
            Object.defineProperty(navigator, 'webdriver', {
                configurable: true,
                get: function() { return undefined; }
            });
            Object.defineProperty(window.screen, 'width', { configurable: true, get: function() { return 1280; } });
            Object.defineProperty(window.screen, 'availWidth', { configurable: true, get: function() { return 1280; } });
        } catch(e) {}
    })();
"""

@Composable
fun SpotifyLoginScreen(
    navController: NavController,
    initialSpDc: String? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Web Login, 1 = Cookie (sp_dc)

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    var cookieInput by remember { mutableStateOf(initialSpDc.orEmpty()) }
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

    // Handle initial deep-link sp_dc if provided
    LaunchedEffect(initialSpDc) {
        if (!initialSpDc.isNullOrBlank()) {
            val sanitized = CookieSanitizer.sanitizeSpDc(initialSpDc) ?: initialSpDc.trim()
            if (sanitized.isNotBlank()) {
                cookieInput = sanitized
                if (tokenFetchStarted.compareAndSet(false, true)) {
                    finishLogin(
                        webViewRef, context as Activity, scope,
                        spDc = sanitized,
                        setProcessing = { isProcessing = it },
                        setStatus = { statusMessage = it },
                        setError = { hasError = it },
                        tokenFetchStarted = tokenFetchStarted,
                        onSuccess = navigateToHome,
                    )
                }
            }
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
                // WEB LOGIN TAB (1280px Desktop Viewport Emulation)
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
                                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                val webSettings = this.settings
                                webSettings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    setSupportMultipleWindows(false)
                                    javaScriptCanOpenWindowsAutomatically = true
                                    allowContentAccess = true
                                    allowFileAccess = false
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    userAgentString = DESKTOP_USER_AGENT
                                }

                                if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
                                    val metadata = UserAgentMetadata.Builder()
                                        .setPlatform("Windows")
                                        .setPlatformVersion("10.0.0")
                                        .setArchitecture("x86")
                                        .setModel("")
                                        .setMobile(false)
                                        .setBitness(64)
                                        .setFullVersion("131.0.6778.86")
                                        .setBrandVersionList(
                                            listOf(
                                                UserAgentMetadata.BrandVersion.Builder()
                                                    .setBrand("Google Chrome")
                                                    .setMajorVersion("131")
                                                    .setFullVersion("131.0.6778.86")
                                                    .build(),
                                                UserAgentMetadata.BrandVersion.Builder()
                                                    .setBrand("Chromium")
                                                    .setMajorVersion("131")
                                                    .setFullVersion("131.0.6778.86")
                                                    .build(),
                                                UserAgentMetadata.BrandVersion.Builder()
                                                    .setBrand("Not_A Brand")
                                                    .setMajorVersion("24")
                                                    .setFullVersion("24.0.0.0")
                                                    .build()
                                            )
                                        )
                                        .build()
                                    WebSettingsCompat.setUserAgentMetadata(webSettings, metadata)
                                }

                                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(webSettings, false)
                                }
                                if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                                    WebSettingsCompat.setForceDark(webSettings, WebSettingsCompat.FORCE_DARK_OFF)
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress / 100f
                                        if (newProgress >= 30) {
                                            view?.evaluateJavascript(VIEWPORT_OVERRIDE_JS, null)
                                            view?.evaluateJavascript(ANTI_BOT_SPOOF_JS, null)
                                        }
                                        if (newProgress >= 80) isWebLoading = false
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        Log.d(TAG, "Web starting: $url")
                                        webError = null
                                        isWebLoading = true
                                        view?.evaluateJavascript(ANTI_BOT_SPOOF_JS, null)
                                        view?.evaluateJavascript(VIEWPORT_OVERRIDE_JS, null)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        Log.d(TAG, "Web finished: $url")
                                        isWebLoading = false
                                        loadProgress = 1f
                                        view?.evaluateJavascript(ANTI_BOT_SPOOF_JS, null)
                                        view?.evaluateJavascript(VIEWPORT_OVERRIDE_JS, null)
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
                                        val url = request?.url?.toString() ?: return false
                                        if (url.startsWith("spotufi://") || url.startsWith("atify://")) {
                                            val spDc = request.url?.getQueryParameter("sp_dc") ?: request.url?.getQueryParameter("cookie")
                                            if (!spDc.isNullOrBlank()) {
                                                if (tokenFetchStarted.compareAndSet(false, true)) {
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
                                                return true
                                            }
                                        }
                                        return false
                                    }
                                }

                                loadUrl(LOGIN_URL, DESKTOP_HEADERS)
                            }
                        },
                        onRelease = { wv ->
                            runCatching {
                                wv.stopLoading()
                                wv.removeAllViews()
                                wv.destroy()
                            }
                        }
                    )

                    // Floating Action Bar at Bottom of Web Tab
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
                            OutlinedButton(
                                onClick = {
                                    CustomTabsHelper.openCustomTab(context, LOGIN_URL)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AtifySand)
                            ) {
                                Text("Open in CCT", fontSize = 12.sp)
                            }

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
                                    webViewRef?.loadUrl(LOGIN_URL, DESKTOP_HEADERS)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AtifySage)
                            ) {
                                Text("Retry Web View")
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    CustomTabsHelper.openCustomTab(context, LOGIN_URL)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AtifySand)
                            ) {
                                Text("Open in Chrome Custom Tab", fontSize = 13.sp)
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
                                                val sanitized = CookieSanitizer.sanitizeSpDc(text) ?: text
                                                cookieInput = sanitized
                                                cookieError = null
                                                if (sanitized != text) {
                                                    Toast.makeText(context, "Sanitized sp_dc from clipboard", Toast.LENGTH_SHORT).show()
                                                }
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
                                        val clean = CookieSanitizer.sanitizeSpDc(cookieInput) ?: cookieInput.trim()
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

                    // Instructions Card with Chrome Custom Tab launcher
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
                                    CustomTabsHelper.openCustomTab(context, LOGIN_URL)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AtifySand)
                            ) {
                                Text("Open Spotify in Chrome Custom Tab", fontSize = 12.sp)
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
    cookieManager.flush()
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
                    return CookieSanitizer.sanitizeSpDc(value) ?: value
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
    val cleanSpDc = CookieSanitizer.sanitizeSpDc(spDc) ?: spDc.trim()
    if (cleanSpDc.isBlank()) {
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

    // Sync CookieManager across all 5 Spotify domains
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    val domains = listOf(
        "https://.spotify.com",
        "https://accounts.spotify.com",
        "https://open.spotify.com",
        "https://api.spotify.com",
        "https://spotify.com"
    )
    val cookieAttrs = "sp_dc=$cleanSpDc; Domain=.spotify.com; Path=/; Secure; SameSite=Lax"
    for (domain in domains) {
        cookieManager.setCookie(domain, cookieAttrs)
    }
    cookieManager.flush()

    scope.launch(Dispatchers.IO) {
        SpotifySession.setSpDc(activity, cleanSpDc)
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            val result = SpotifyAuth.fetchAccessToken(cleanSpDc)
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

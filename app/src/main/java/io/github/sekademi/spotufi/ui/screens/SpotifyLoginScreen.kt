package io.github.sekademi.spotufi.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
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
import io.github.sekademi.spotufi.ui.theme.AtifyCream
import io.github.sekademi.spotufi.ui.theme.AtifySand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

private const val CHROME_MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

@Composable
fun SpotifyLoginScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Web Login, 1: Cookie Input
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var pageError by remember { mutableStateOf<String?>(null) }

    val tokenFetchStarted = remember { AtomicBoolean(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Direct cookie state
    var manualCookie by remember { mutableStateOf("") }

    val navigateToHome: () -> Unit = {
        io.github.sekademi.spotufi.di.SpotifyWebPlayer.refreshLogin(context)
        navController.navigate(Routes.Home.route) {
            popUpTo(Routes.Login.route) { inclusive = true }
        }
    }

    // Continuously monitor CookieManager for sp_dc during Web Login
    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) {
            while (true) {
                delay(600)
                if (tokenFetchStarted.get()) continue
                val spDc = extractCookie("sp_dc")
                if (!spDc.isNullOrBlank() && tokenFetchStarted.compareAndSet(false, true)) {
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
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AtifyDark)
            .statusBarsPadding()
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
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
                Spacer(Modifier.width(12.dp))
            }

            Text(
                text = if (isProcessing) statusMessage.ifBlank { "Signing in…" } else "Log in to Spotify",
                color = if (hasError) Color(0xFFFF5252) else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier.weight(1f)
            )

            if (selectedTab == 0) {
                IconButton(onClick = {
                    pageError = null
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

        // Tabs: Web Login vs Direct Cookie
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = AtifyDark,
            contentColor = Color.White,
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(selectedTab),
                    color = AtifySage
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Web Login", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                },
                selectedContentColor = AtifySage,
                unselectedContentColor = Color.Gray
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cookie (sp_dc)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                },
                selectedContentColor = AtifySage,
                unselectedContentColor = Color.Gray
            )
        }

        // Progress Bar
        if (selectedTab == 0 && loadProgress in 0.01f..0.99f) {
            LinearProgressIndicator(
                progress = { loadProgress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = AtifySage,
                trackColor = Color.Black
            )
        }

        // Main Content Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
        ) {
            if (selectedTab == 0) {
                // Tab 0: Spotify Web Login WebView
                if (pageError != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Connection Error",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = pageError ?: "Unable to load Spotify login page.",
                            color = Color.LightGray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = {
                                pageError = null
                                webViewRef?.loadUrl(SpotifyAuth.LOGIN_URL)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AtifySage)
                        ) {
                            Text("Retry Web Login", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Or switch to the 'Cookie (sp_dc)' tab above",
                            color = AtifySand,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { selectedTab = 1 }
                        )
                    }
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)

                        WebView(ctx).apply {
                            webViewRef = this
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                setSupportMultipleWindows(true)
                                allowContentAccess = true
                                allowFileAccess = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                userAgentString = CHROME_MOBILE_UA
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress / 100f
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    pageError = null
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loadProgress = 1f
                                    val spDc = extractCookie("sp_dc")
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
                                        pageError = error?.description?.toString() ?: "Network error"
                                    }
                                }

                                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                    handler?.proceed()
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    return false
                                }
                            }

                            loadUrl(SpotifyAuth.LOGIN_URL)
                        }
                    }
                )

                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AtifySage, strokeWidth = 3.dp)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = statusMessage.ifBlank { "Authenticating with Spotify…" },
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            } else {
                // Tab 1: Direct sp_dc Cookie Entry
                DirectCookieLoginForm(
                    cookie = manualCookie,
                    onCookieChange = { manualCookie = it },
                    isProcessing = isProcessing,
                    statusMessage = statusMessage,
                    hasError = hasError,
                    onSubmit = {
                        val clean = manualCookie.trim()
                        if (clean.isBlank()) {
                            hasError = true
                            statusMessage = "Please enter your sp_dc cookie"
                            return@DirectCookieLoginForm
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
                    }
                )
            }
        }
    }
}

@Composable
private fun DirectCookieLoginForm(
    cookie: String,
    onCookieChange: (String) -> Unit,
    isProcessing: Boolean,
    statusMessage: String,
    hasError: Boolean,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "Atify",
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Direct Cookie Login",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Paste your Spotify `sp_dc` cookie below for direct, guaranteed login without browser challenges.",
            color = Color(0xFFB3B3B3),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        Text(
            "Spotify sp_dc Cookie",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = cookie,
            onValueChange = onCookieChange,
            singleLine = false,
            maxLines = 4,
            placeholder = { Text("AQB... (sp_dc cookie string)", color = Color(0xFF666666)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = AtifySage,
                focusedBorderColor = AtifySage,
                unfocusedBorderColor = Color(0xFF333333),
                focusedContainerColor = Color(0xFF141414),
                unfocusedContainerColor = Color(0xFF141414),
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        if (statusMessage.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                statusMessage,
                color = if (hasError) Color(0xFFFF5252) else AtifySage,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSubmit,
            enabled = !isProcessing && cookie.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AtifySage,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF2E3D34),
                disabledContentColor = Color.Gray,
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text("Log In with Cookie", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        Spacer(Modifier.height(28.dp))

        // Step-by-step help card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141A17))
                .padding(16.dp)
        ) {
            Text(
                "How to find your `sp_dc` cookie in 30 seconds:",
                color = AtifySand,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "1. Open open.spotify.com in your browser (Chrome/Edge/Firefox/Brave) and log in.\n" +
                "2. Press F12 (or right-click → Inspect) to open Developer Tools.\n" +
                "3. Click the Application (or Storage) tab.\n" +
                "4. Under Storage → Cookies, select https://open.spotify.com.\n" +
                "5. Find the cookie named sp_dc and copy its value.",
                color = Color(0xFF9E9E9E),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

private fun extractCookie(name: String): String? {
    val allCookies = CookieManager.getInstance().getCookie("https://open.spotify.com") ?: return null
    return allCookies.split(";")
        .mapNotNull {
            val parts = it.trim().split("=", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
        }
        .firstOrNull { it.first == name && it.second.isNotBlank() }
        ?.second
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
        setStatus("Invalid or empty login cookie. Please try again.")
        tokenFetchStarted.set(false)
        return
    }

    setProcessing(true)
    setError(false)
    setStatus("Connecting…")
    view?.stopLoading()

    scope.launch(Dispatchers.IO) {
        SpotifySession.setSpDc(activity, spDc)
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            val result = SpotifyAuth.fetchAccessToken(spDc)
            result.onSuccess { token ->
                Spotify.accessToken = token.accessToken
                withContext(Dispatchers.Main) { setStatus("Success!") }
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

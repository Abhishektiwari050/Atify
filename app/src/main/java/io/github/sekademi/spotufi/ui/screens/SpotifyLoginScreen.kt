package io.github.sekademi.spotufi.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.sekademi.spotufi.ui.theme.AtifySand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SpotifyLogin"

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableFloatStateOf(0.1f) }
    var isLoading by remember { mutableStateOf(true) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var showCookieDialog by remember { mutableStateOf(false) }

    val tokenFetchStarted = remember { AtomicBoolean(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val navigateToHome: () -> Unit = {
        io.github.sekademi.spotufi.di.SpotifyWebPlayer.refreshLogin(context)
        navController.navigate(Routes.Home.route) {
            popUpTo(Routes.Login.route) { inclusive = true }
        }
    }

    // Continuously monitor CookieManager across all Spotify domains
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            if (tokenFetchStarted.get()) continue
            val spDc = extractSpotifyDcCookie()
            if (!spDc.isNullOrBlank() && tokenFetchStarted.compareAndSet(false, true)) {
                Log.d(TAG, "sp_dc cookie captured successfully!")
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
            .background(Color(0xFF121212))
            .statusBarsPadding()
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(AtifyDark)
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

            IconButton(onClick = {
                pageError = null
                isLoading = true
                loadProgress = 0.1f
                webViewRef?.reload()
            }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reload",
                    tint = Color.White
                )
            }

            IconButton(onClick = { showCookieDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Paste Cookie",
                    tint = AtifySand
                )
            }
        }

        // Live Loading Progress Bar
        if (isLoading && loadProgress in 0.01f..0.99f) {
            LinearProgressIndicator(
                progress = { loadProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = Color(0xFF1ED760),
                trackColor = Color(0xFF282828)
            )
        }

        // Main In-App WebView Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF121212))
        ) {
            // Android WebView
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
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            setSupportMultipleWindows(false)
                            javaScriptCanOpenWindowsAutomatically = true
                            allowContentAccess = true
                            allowFileAccess = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            userAgentString = DESKTOP_UA
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress / 100f
                                if (newProgress >= 90) {
                                    isLoading = false
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                Log.d(TAG, "onPageStarted: $url")
                                pageError = null
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                Log.d(TAG, "onPageFinished: $url")
                                isLoading = false
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
                                    Log.e(TAG, "onReceivedError: ${error?.description}")
                                    pageError = error?.description?.toString() ?: "Network connection error"
                                    isLoading = false
                                }
                            }

                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                handler?.proceed()
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString().orEmpty()
                                Log.d(TAG, "shouldOverrideUrlLoading: $url")
                                return false // Allow in-place navigation
                            }
                        }

                        loadUrl("https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F")
                    }
                }
            )

            // Loading Spinner while page is loading
            if (isLoading && pageError == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF121212).copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color(0xFF1ED760),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Loading Spotify login…",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Connection Error Fallback
            if (pageError != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF121212))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Unable to Load Spotify",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = pageError ?: "Check your internet connection and try again.",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            pageError = null
                            isLoading = true
                            loadProgress = 0.1f
                            webViewRef?.loadUrl("https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AtifySage)
                    ) {
                        Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Or tap the lock icon (🔒) above to paste your sp_dc cookie",
                        color = AtifySand,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { showCookieDialog = true }
                    )
                }
            }

            // Fullscreen Processing Overlay when login succeeded
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
                            color = Color(0xFF1ED760),
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

    // Direct Cookie Input Dialog (Accessible via Lock Icon)
    if (showCookieDialog) {
        var inputCookie by remember { mutableStateOf("") }
        var dialogError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showCookieDialog = false },
            containerColor = Color(0xFF18251F),
            titleContentColor = Color.White,
            title = {
                Text("Direct sp_dc Cookie Login", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column {
                    Text(
                        "Paste your Spotify sp_dc cookie to authenticate instantly without any browser challenges.",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = inputCookie,
                        onValueChange = {
                            inputCookie = it
                            dialogError = null
                        },
                        placeholder = { Text("AQB... (sp_dc value)", color = Color.Gray) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AtifySage,
                            unfocusedBorderColor = Color.DarkGray,
                            cursorColor = AtifySage
                        )
                    )
                    if (dialogError != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(dialogError!!, color = Color(0xFFFF5252), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "How to get sp_dc: Log in to open.spotify.com on desktop → F12 → Application → Cookies → copy 'sp_dc'.",
                        color = AtifySand,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = inputCookie.trim()
                        if (clean.isBlank()) {
                            dialogError = "Cookie cannot be empty"
                            return@Button
                        }
                        showCookieDialog = false
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
                    colors = ButtonDefaults.buttonColors(containerColor = AtifySage)
                ) {
                    Text("Log In", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCookieDialog = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            }
        )
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

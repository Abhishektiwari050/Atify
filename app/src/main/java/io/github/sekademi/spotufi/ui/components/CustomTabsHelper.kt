package io.github.sekademi.spotufi.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import timber.log.Timber

/**
 * Helper to launch Chrome Custom Tabs (CCT) styled with Atify's dark palette.
 * Provides safe fallback to standard browser intents if Custom Tabs is unavailable.
 */
object CustomTabsHelper {
    private const val TOOLBAR_COLOR = 0xFF18251F.toInt()
    private const val NAVIGATION_BAR_COLOR = 0xFF121212.toInt()

    fun openCustomTab(context: Context, url: String) {
        val uri = Uri.parse(url)
        try {
            val colorSchemeParams = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(TOOLBAR_COLOR)
                .setNavigationBarColor(NAVIGATION_BAR_COLOR)
                .build()

            val customTabsIntent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colorSchemeParams)
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .build()

            if (context !is Activity) {
                customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            customTabsIntent.launchUrl(context, uri)
        } catch (e: Exception) {
            Timber.w(e, "Custom Tabs launch failed, falling back to ACTION_VIEW: %s", url)
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(fallbackIntent)
            } catch (fallbackError: Exception) {
                Timber.e(fallbackError, "Failed to open URL in any browser: %s", url)
            }
        }
    }
}

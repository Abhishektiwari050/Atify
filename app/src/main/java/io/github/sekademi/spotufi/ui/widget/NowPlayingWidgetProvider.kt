package io.github.sekademi.spotufi.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import io.github.sekademi.spotufi.MainActivity
import io.github.sekademi.spotufi.R
import io.github.sekademi.spotufi.di.SongPlayer

/**
 * Modern Material-styled home screen widget provider for Atify.
 * Displays live track artwork, title, artist, and Play/Pause/Skip controls.
 */
class NowPlayingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                if (SongPlayer.isPlaying()) {
                    SongPlayer.pause()
                } else {
                    SongPlayer.play()
                }
            }
            ACTION_NEXT -> {
                // Send standard media button NEXT intent to system/service
                val nextIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_NEXT))
                }
                context.sendBroadcast(nextIntent)
            }
            ACTION_PREV -> {
                val prevIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS))
                }
                context.sendBroadcast(prevIntent)
            }
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "io.github.sekademi.spotufi.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "io.github.sekademi.spotufi.widget.NEXT"
        const val ACTION_PREV = "io.github.sekademi.spotufi.widget.PREV"

        @Volatile private var cachedTitle: String = ""
        @Volatile private var cachedArtist: String = ""
        @Volatile private var cachedIsPlaying: Boolean = false
        @Volatile private var cachedCoverBitmap: Bitmap? = null

        fun updateAllWidgets(
            context: Context,
            title: String,
            artist: String,
            isPlaying: Boolean,
            coverUri: String = "",
        ) {
            cachedTitle = title
            cachedArtist = artist
            cachedIsPlaying = isPlaying

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, NowPlayingWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            if (ids.isEmpty()) return

            if (coverUri.isNotBlank()) {
                val request = ImageRequest.Builder(context)
                    .data(coverUri)
                    .size(Size(120, 120))
                    .allowHardware(false)
                    .target(
                        onSuccess = { result ->
                            cachedCoverBitmap = result.toBitmap()
                            for (id in ids) {
                                renderViews(context, appWidgetManager, id, title, artist, isPlaying, cachedCoverBitmap)
                            }
                        },
                        onError = {
                            for (id in ids) {
                                renderViews(context, appWidgetManager, id, title, artist, isPlaying, null)
                            }
                        }
                    )
                    .build()
                context.imageLoader.enqueue(request)
            } else {
                cachedCoverBitmap = null
                for (id in ids) {
                    renderViews(context, appWidgetManager, id, title, artist, isPlaying, null)
                }
            }
        }

        private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            renderViews(
                context = context,
                appWidgetManager = appWidgetManager,
                widgetId = widgetId,
                title = cachedTitle.ifBlank { "Atify" },
                artist = cachedArtist.ifBlank { "Tap to play" },
                isPlaying = cachedIsPlaying,
                coverBitmap = cachedCoverBitmap,
            )
        }

        private fun renderViews(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int,
            title: String,
            artist: String,
            isPlaying: Boolean,
            coverBitmap: Bitmap?,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_now_playing)

            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)

            if (coverBitmap != null && !coverBitmap.isRecycled) {
                views.setImageViewBitmap(R.id.widget_cover, coverBitmap)
            } else {
                views.setImageViewResource(R.id.widget_cover, R.mipmap.ic_launcher)
            }

            views.setImageViewResource(
                R.id.widget_btn_play_pause,
                if (isPlaying) R.drawable.ic_playing else R.drawable.ic_paused,
            )

            // Open app on body click
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

            // Play/Pause broadcast
            val playPauseIntent = Intent(context, NowPlayingWidgetProvider::class.java).apply {
                action = ACTION_PLAY_PAUSE
            }
            val playPausePendingIntent = PendingIntent.getBroadcast(
                context, 1, playPauseIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePendingIntent)

            // Next broadcast
            val nextIntent = Intent(context, NowPlayingWidgetProvider::class.java).apply {
                action = ACTION_NEXT
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context, 2, nextIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_btn_next, nextPendingIntent)

            // Previous broadcast
            val prevIntent = Intent(context, NowPlayingWidgetProvider::class.java).apply {
                action = ACTION_PREV
            }
            val prevPendingIntent = PendingIntent.getBroadcast(
                context, 3, prevIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_btn_prev, prevPendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}

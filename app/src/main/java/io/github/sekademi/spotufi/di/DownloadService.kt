package io.github.sekademi.spotufi.di

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.sekademi.spotufi.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground Service that keeps batch downloads active in the background
 * without being killed by Android OS battery optimization or process management.
 */
class DownloadService : Service() {

    companion object {
        const val NOTIFICATION_ID = 8081
        const val CHANNEL_ID = "atify_downloads"

        fun start(context: Context) {
            try {
                val intent = Intent(context, DownloadService::class.java)
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                android.util.Log.w("DownloadService", "Failed to start foreground download service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, DownloadService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                android.util.Log.w("DownloadService", "Failed to stop download service: ${e.message}")
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        acquireWakeLock()
        startForegroundNotification()
        startMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Track download progress"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Atify:DownloadWakeLock")?.apply {
                acquire(60 * 60 * 1000L) // 1 hour safety timeout
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }

    private fun buildNotification(remaining: Int, progress: Int): android.app.Notification {
        val title = if (remaining > 0) "Downloading music ($remaining remaining)" else "Downloads finished"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(if (remaining > 0) "Saving tracks for offline playback" else "All tracks saved to library")
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0 && remaining > 0)
            .setOngoing(remaining > 0)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun startForegroundNotification() {
        val notification = buildNotification(DownloadManager.activeCount(), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startMonitor() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                val active = DownloadManager.activeCount()
                val snapshot = DownloadManager.downloadingSnapshot()
                val avgProgress = if (snapshot.isEmpty()) 0 else snapshot.map { it.second }.average().toInt()

                if (active == 0) {
                    delay(1200)
                    if (DownloadManager.activeCount() == 0) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        break
                    }
                } else {
                    val nm = getSystemService(NotificationManager::class.java)
                    nm?.notify(NOTIFICATION_ID, buildNotification(active, avgProgress))
                }
                delay(1000)
            }
        }
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        monitorJob = null
        releaseWakeLock()
        super.onDestroy()
    }
}

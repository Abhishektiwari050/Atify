package io.github.sekademi.spotufi.util

import android.content.Context
import android.os.Build
import io.github.sekademi.spotufi.BuildConfig
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local offline crash logger that writes uncaught exception stack traces to
 * internal storage (`filesDir/crashes/`) without third-party trackers.
 */
object CrashLogger {

    private const val CRASH_DIR_NAME = "crashes"
    private const val MAX_SAVED_CRASHES = 10
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true

        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordCrash(appContext, thread, throwable)
            } catch (e: Throwable) {
                android.util.Log.e("CrashLogger", "Failed to record crash locally", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun recordCrash(context: Context, thread: Thread, throwable: Throwable) {
        Timber.e(throwable, "Uncaught exception on thread: %s", thread.name)

        val crashDir = File(context.filesDir, CRASH_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val timestamp = dateFormat.format(Date())
        val crashFile = File(crashDir, "crash_$timestamp.txt")

        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = sw.toString()

        val report = buildString {
            appendLine("=== Atify Crash Report ===")
            appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())}")
            appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Package: ${context.packageName}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine("----------------------------------------")
            appendLine("Exception: ${throwable.javaClass.name}")
            appendLine("Message: ${throwable.message}")
            appendLine("----------------------------------------")
            appendLine("Stack Trace:")
            appendLine(stackTrace)
            appendLine("========================================")
        }

        crashFile.writeText(report)
        pruneOldCrashes(crashDir)
    }

    private fun pruneOldCrashes(crashDir: File) {
        val files = crashDir.listFiles { file -> file.isFile && file.name.startsWith("crash_") } ?: return
        if (files.size > MAX_SAVED_CRASHES) {
            files.sortedBy { it.lastModified() }
                .take(files.size - MAX_SAVED_CRASHES)
                .forEach { it.delete() }
        }
    }

    fun getCrashReports(context: Context): List<File> {
        val crashDir = File(context.filesDir, CRASH_DIR_NAME)
        if (!crashDir.exists()) return emptyList()
        return crashDir.listFiles { file -> file.isFile && file.name.startsWith("crash_") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    fun clearCrashReports(context: Context) {
        val crashDir = File(context.filesDir, CRASH_DIR_NAME)
        if (crashDir.exists()) {
            crashDir.listFiles()?.forEach { it.delete() }
        }
    }
}

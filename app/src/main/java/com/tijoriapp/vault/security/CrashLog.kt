package com.tijoriapp.vault.security

import android.content.Context
import android.os.Build
import com.tijoriapp.vault.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Offline crash reports. Tijori has no internet, so instead of uploading anything we keep the last crash
 * on the phone and show it on the next launch, where the user can copy it and send it to the developer.
 * Contains only the error and stack trace: never vault data.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(appContext.filesDir, FILE).writeText(report(error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = runCatching {
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText()
    }.getOrNull()

    fun clear(context: Context) { runCatching { File(context.filesDir, FILE).delete() } }

    private fun report(e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        return buildString {
            appendLine("Tijori ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Time: ${java.util.Date()}")
            appendLine()
            append(sw.toString().take(12_000))
        }
    }
}

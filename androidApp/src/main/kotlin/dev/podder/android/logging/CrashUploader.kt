package dev.podder.android.logging

import android.content.Context
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * Best-effort uploader for crash reports collected by CrashContextHarvester.
 *
 * Called on the next app launch (from an IO coroutine in PodderApplication) after
 * a crash context file is found. Uses plain HttpURLConnection — no Ktor, no Koin,
 * nothing that could itself fail in a degraded post-crash state.
 *
 * Destination: crash_receiver.py running on the dev machine over Tailscale.
 * On failure the report is silently dropped — the app already cleared the file,
 * so we don't retry. Crashes are development signals, not critical data.
 */
object CrashUploader {

    private const val ENDPOINT = "http://100.78.163.64:7779/crash"
    private const val TIMEOUT_MS = 10_000

    fun upload(context: Context, crashContext: String) {
        try {
            val version = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrDefault("unknown")

            val report = buildString {
                appendLine("timestamp=${Instant.now()}")
                appendLine("version=$version")
                appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("android=${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("---")
                append(crashContext)
            }

            val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
            conn.requestMethod  = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout    = TIMEOUT_MS
            conn.doOutput       = true
            conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            conn.outputStream.use { it.write(report.toByteArray(Charsets.UTF_8)) }
            conn.responseCode   // triggers the request
            conn.disconnect()
        } catch (_: Exception) {
            // Never let the uploader crash the app
        }
    }
}

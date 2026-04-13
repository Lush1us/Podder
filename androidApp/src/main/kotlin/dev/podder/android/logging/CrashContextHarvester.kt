package com.lush1us.podder.logging

import android.app.ActivityManager
import android.content.Context
import android.util.AtomicFile
import dev.podder.domain.model.PlaybackState
import dev.podder.domain.player.PlaybackStateMachine
import java.io.File

/**
 * Collects a rich diagnostic snapshot at crash time and persists it atomically.
 * Designed to run synchronously inside an UncaughtExceptionHandler — no coroutines.
 */
class CrashContextHarvester(
    private val context: Context,
    private val appLogger: AppPodderLogger,
) {
    // Wired after Koin starts — safe because crashes happen after startup
    var stateMachine: PlaybackStateMachine? = null

    fun harvest(thread: Thread, throwable: Throwable): String = buildString {
        appendLine("=== PODDER CRASH CONTEXT ===")
        appendLine("Thread: ${thread.name} (id=${thread.id})")
        appendLine()

        // Exception
        appendLine("--- Exception ---")
        appendLine(throwable.stackTraceToString())
        appendLine()

        // Playback state
        appendLine("--- Playback State ---")
        val ps = stateMachine?.state?.value
        appendLine(when (ps) {
            is PlaybackState.Playing   -> "Playing  trackId=${ps.trackId} pos=${ps.positionMs}ms dur=${ps.durationMs}ms"
            is PlaybackState.Paused    -> "Paused   trackId=${ps.trackId} pos=${ps.positionMs}ms"
            is PlaybackState.Buffering -> "Buffering trackId=${ps.trackId}"
            is PlaybackState.Error     -> "Error    trackId=${ps.trackId} msg=${ps.message}"
            is PlaybackState.Idle,
            null                       -> "Idle / unavailable"
        })
        appendLine()

        // Memory
        appendLine("--- Memory ---")
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            appendLine("availMem=${memInfo.availMem / 1024 / 1024} MB  totalMem=${memInfo.totalMem / 1024 / 1024} MB  lowMemory=${memInfo.lowMemory}")
        } catch (e: Exception) {
            appendLine("unavailable: ${e.message}")
        }
        appendLine()

        // Thread dump
        appendLine("--- All Thread Stacks ---")
        Thread.getAllStackTraces().forEach { (t, frames) ->
            appendLine("Thread[${t.name}] state=${t.state}")
            frames.forEach { appendLine("  at $it") }
        }
        appendLine()

        // Ring buffer (last 500 log lines)
        appendLine("--- Recent Logs (ring buffer) ---")
        appLogger.ringBufferSnapshot().forEach { appendLine(it) }
    }

    fun persistToDisk(payload: String) {
        val file = File(context.filesDir, "crash_context.txt")
        val atomicFile = AtomicFile(file)
        val fos = try { atomicFile.startWrite() } catch (_: Exception) { return }
        try {
            fos.write(payload.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(fos)
        } catch (_: Exception) {
            atomicFile.failWrite(fos)
        }
    }
}

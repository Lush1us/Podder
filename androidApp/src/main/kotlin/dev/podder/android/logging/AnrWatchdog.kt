package com.lush1us.podder.logging

import android.os.Handler
import android.os.Looper
import dev.podder.logging.LogEvent
import dev.podder.logging.LogLevel
import dev.podder.logging.PodderLogger
import dev.podder.logging.Subsystem

/**
 * Daemon thread that detects main-thread freezes before the OS fires its 5-second ANR.
 * Posts a lightweight Runnable to the main Handler every cycle; if the tick hasn't
 * advanced after [timeoutMs] sleep, the main thread is blocked and we log it.
 */
class AnrWatchdog(
    private val logger: PodderLogger,
    private val timeoutMs: Long = 4_000L,
) : Thread("PodderAnrWatchdog") {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var tick = 0
    @Volatile private var lastReportedTick = -1

    private val tickRunnable = Runnable {
        tick = (tick + 1) % Int.MAX_VALUE
    }

    init {
        isDaemon = true
    }

    override fun run() {
        while (!isInterrupted) {
            val before = tick
            mainHandler.post(tickRunnable)

            try {
                sleep(timeoutMs)
            } catch (_: InterruptedException) {
                return
            }

            if (tick == before && lastReportedTick != before) {
                lastReportedTick = before
                val stackTrace = Looper.getMainLooper().thread.stackTrace
                    .joinToString("\n") { "  at $it" }
                logger.log(
                    LogLevel.ERROR,
                    Subsystem.APP,
                    LogEvent.Performance.AnrDetected(
                        blockedMs        = timeoutMs,
                        mainThreadStack  = stackTrace,
                    )
                )
            }
        }
    }
}

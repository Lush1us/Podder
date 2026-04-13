package com.lush1us.podder.logging

import android.view.Choreographer
import dev.podder.logging.LogEvent
import dev.podder.logging.LogLevel
import dev.podder.logging.PodderLogger
import dev.podder.logging.Subsystem

/**
 * Detects dropped frames by measuring the delta between Choreographer Vsync callbacks.
 * Logs LogEvent.Performance.JankDetected when ≥3 frames are dropped consecutively.
 * Must be started and stopped on the main thread.
 */
class JankMonitor(private val logger: PodderLogger) : Choreographer.FrameCallback {

    private val frameIntervalNs = 16_666_666L  // 60 Hz baseline
    private val jankThresholdFrames = 3
    private var lastFrameTimeNs = 0L
    private var running = false

    fun start() {
        running = true
        lastFrameTimeNs = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        running = false
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (lastFrameTimeNs != 0L) {
            val delta = frameTimeNanos - lastFrameTimeNs
            val dropped = (delta / frameIntervalNs).toInt()
            if (dropped >= jankThresholdFrames) {
                logger.log(
                    LogLevel.WARN,
                    Subsystem.UI,
                    LogEvent.Performance.JankDetected(
                        droppedFrames = dropped,
                        deltaMs       = delta / 1_000_000L,
                    )
                )
            }
        }
        lastFrameTimeNs = frameTimeNanos
        Choreographer.getInstance().postFrameCallback(this)
    }
}

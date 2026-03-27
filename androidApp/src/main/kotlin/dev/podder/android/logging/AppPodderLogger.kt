package dev.podder.android.logging

import dev.podder.logging.LogEvent
import dev.podder.logging.LogLevel
import dev.podder.logging.PodderLogger
import dev.podder.logging.Subsystem
import dev.podder.logging.TelemetryEnvelope
import dev.podder.logging.telemetryJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Concrete logger for Android. Created once in PodderApplication before Koin starts,
 * then provided to Koin as a singleton so all classes receive it via injection.
 *
 * Routing:
 *   VERBOSE / DEBUG → ring buffer + Logcat (all builds)
 *   INFO+           → ring buffer + Logcat + NDJSON file
 *   ERROR           → additionally triggers a ring-buffer flush to crash_context.txt
 */
class AppPodderLogger(private val logDir: File) : PodderLogger {

    private val ringBuffer = RingBuffer(capacity = 500)
    private val fileLogger = SizeCappedFileLogger(logDir)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val crashContextFile get() = File(logDir.parent, "crash_context.txt")

    override fun log(
        level: LogLevel,
        subsystem: Subsystem,
        event: LogEvent,
        throwable: Throwable?,
    ) {
        val envelope = TelemetryEnvelope(
            timestampMs = System.currentTimeMillis(),
            level       = level.name,
            subsystem   = subsystem.name,
            event       = event,
        )
        val line = try {
            telemetryJson.encodeToString(TelemetryEnvelope.serializer(), envelope)
        } catch (_: Exception) {
            // Never let serialisation crash the app
            "{\"error\":\"serialisation_failed\",\"subsystem\":\"${subsystem.name}\"}"
        }

        ringBuffer.append(line)
        LogcatAppender.append(level, line)

        if (level >= LogLevel.INFO) {
            scope.launch { fileLogger.append(line) }
        }
    }

    /** Returns the last N lines from the in-memory ring buffer (oldest first). */
    fun ringBufferSnapshot(): List<String> = ringBuffer.snapshot()

    /**
     * Read the crash context file written on the previous run, then delete it.
     * Returns the content if found, null otherwise. Call from a background coroutine.
     */
    fun readAndClearCrashContext(): String? {
        val file = crashContextFile
        if (!file.exists()) return null
        return try {
            val content = file.readText(Charsets.UTF_8)
            file.delete()
            content
        } catch (_: Exception) {
            null
        }
    }
}

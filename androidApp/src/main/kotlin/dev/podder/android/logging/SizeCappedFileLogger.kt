package com.lush1us.podder.logging

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

/**
 * NDJSON rolling file logger.
 * Maintains up to [maxFiles] files of [maxFileSizeBytes] each inside [logDir].
 * Files are named podder_log_0.ndjson (active) … podder_log_N-1.ndjson (oldest).
 * Only INFO+ events are written; call sites enforce this upstream.
 */
internal class SizeCappedFileLogger(
    private val logDir: File,
    private val maxFileSizeBytes: Long = 5L * 1024 * 1024, // 5 MB
    private val maxFiles: Int = 3,
) {
    private val mutex = Mutex()

    suspend fun append(ndjsonLine: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val active = File(logDir, "podder_log_0.ndjson")
            val lineBytes = ndjsonLine.toByteArray(Charsets.UTF_8).size
            if (active.exists() && active.length() + lineBytes > maxFileSizeBytes) {
                rotate()
            }
            BufferedWriter(FileWriter(active, /* append= */ true)).use { w ->
                w.append(ndjsonLine)
                w.newLine()
            }
        }
    }

    private fun rotate() {
        // Delete oldest
        File(logDir, "podder_log_${maxFiles - 1}.ndjson").delete()
        // Cascade: N-2 → N-1, N-3 → N-2 … 0 → 1
        for (i in (maxFiles - 2) downTo 0) {
            val src = File(logDir, "podder_log_$i.ndjson")
            val dst = File(logDir, "podder_log_${i + 1}.ndjson")
            if (src.exists()) src.renameTo(dst)
        }
    }
}

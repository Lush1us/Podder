package dev.podder.util.time

expect fun platformNowUtcEpoch(): Long

object TimeUtils {
    fun nowUtcEpoch(): Long = platformNowUtcEpoch()

    fun Long.toDisplayDuration(): String {
        val totalSeconds = this / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            "$minutes:${seconds.toString().padStart(2, '0')}"
        }
    }
}

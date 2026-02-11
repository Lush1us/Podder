package com.example.podder.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Originator {
    USER,   // User-initiated actions (button presses, taps)
    APP,    // App-initiated actions (auto-save, restore, cleanup)
    DEVICE  // Device/system events (lifecycle, notifications)
}

object AppLogger {

    private const val TAG = "Podder"
    private const val LOG_FILE_NAME = "podder_events.log"

    // Fixed column widths
    private const val ORIGINATOR_WIDTH = 6
    private const val REQUEST_WIDTH = 16
    private const val ACTION_WIDTH = 20

    private val dateFormat = SimpleDateFormat("MM/dd HH:mm:ss.SSS", Locale.US)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun String.center(width: Int): String {
        if (length >= width) return this
        val padding = width - length
        val leftPad = padding / 2
        val rightPad = padding - leftPad
        return " ".repeat(leftPad) + this + " ".repeat(rightPad)
    }

    fun log(
        context: Context,
        originator: Originator,
        request: String,
        actionTaken: String,
        result: String = ""
    ) {
        val timestamp = dateFormat.format(Date())
        val originatorStr = originator.name.center(ORIGINATOR_WIDTH)
        val requestStr = request.center(REQUEST_WIDTH)
        val actionStr = actionTaken.center(ACTION_WIDTH)

        val logLine = "$timestamp [$originatorStr][$requestStr][$actionStr] $result\n"

        // Print to Logcat for debugging
        Log.d(TAG, logLine.trim())

        // Append to file asynchronously on IO dispatcher
        scope.launch {
            try {
                val logFile = File(context.filesDir, LOG_FILE_NAME)
                logFile.appendText(logLine)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write to log file: ${e.message}", e)
            }
        }
    }

    /**
     * Updates a pending error report entry (within last 5 minutes) with the submitted message.
     * If no pending entry is found, creates a new entry.
     */
    fun updatePendingErrorReport(
        context: Context,
        message: String
    ) {
        val now = System.currentTimeMillis()
        val fiveMinutesAgo = now - 5 * 60 * 1000

        scope.launch {
            try {
                val logFile = File(context.filesDir, LOG_FILE_NAME)
                if (!logFile.exists()) {
                    // No log file, create new entry
                    log(context, Originator.USER, "ErrorReport", "Report Submitted", "Message: $message")
                    return@launch
                }

                val lines = logFile.readLines().toMutableList()
                var foundIndex = -1

                // Search backwards for a pending error report within the time window
                for (i in lines.indices.reversed()) {
                    val line = lines[i]
                    if (line.contains("[  ErrorReport   ]") && line.contains("[   Report Pending   ]")) {
                        // Parse timestamp from the line (format: MM/dd HH:mm:ss.SSS)
                        val timestampStr = line.substring(0, 18)
                        try {
                            val entryTime = dateFormat.parse(timestampStr)?.time ?: continue
                            // Adjust for year (dateFormat doesn't include year, assume current year)
                            val calendar = java.util.Calendar.getInstance()
                            val currentYear = calendar.get(java.util.Calendar.YEAR)
                            calendar.time = dateFormat.parse(timestampStr) ?: continue
                            calendar.set(java.util.Calendar.YEAR, currentYear)
                            val adjustedTime = calendar.timeInMillis

                            if (adjustedTime >= fiveMinutesAgo) {
                                foundIndex = i
                                break
                            }
                        } catch (e: Exception) {
                            continue
                        }
                    }
                }

                if (foundIndex >= 0) {
                    // Update the pending entry
                    val timestamp = dateFormat.format(Date())
                    val originatorStr = Originator.USER.name.center(ORIGINATOR_WIDTH)
                    val requestStr = "ErrorReport".center(REQUEST_WIDTH)
                    val actionStr = "Report Submitted".center(ACTION_WIDTH)
                    val updatedLine = "$timestamp [$originatorStr][$requestStr][$actionStr] Message: $message"

                    lines[foundIndex] = updatedLine
                    logFile.writeText(lines.joinToString("\n") + "\n")
                    Log.d(TAG, updatedLine)
                } else {
                    // No pending entry found, create new
                    log(context, Originator.USER, "ErrorReport", "Report Submitted", "Message: $message")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update error report: ${e.message}", e)
            }
        }
    }
}

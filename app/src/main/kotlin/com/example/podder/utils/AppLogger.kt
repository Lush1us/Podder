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
}

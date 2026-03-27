package dev.podder.android.logging

import android.util.Log
import dev.podder.logging.LogLevel

internal object LogcatAppender {

    private const val TAG = "Podder"

    fun append(level: LogLevel, line: String) {
        when (level) {
            LogLevel.VERBOSE -> Log.v(TAG, line)
            LogLevel.DEBUG   -> Log.d(TAG, line)
            LogLevel.INFO    -> Log.i(TAG, line)
            LogLevel.WARN    -> Log.w(TAG, line)
            LogLevel.ERROR   -> Log.e(TAG, line)
        }
    }
}

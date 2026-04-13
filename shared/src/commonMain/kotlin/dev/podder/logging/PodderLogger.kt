package dev.podder.logging

interface PodderLogger {
    fun log(
        level: LogLevel,
        subsystem: Subsystem,
        event: LogEvent,
        throwable: Throwable? = null,
    )
}

fun PodderLogger.logVerbose(subsystem: Subsystem, event: LogEvent) =
    log(LogLevel.VERBOSE, subsystem, event)

fun PodderLogger.logDebug(subsystem: Subsystem, event: LogEvent) =
    log(LogLevel.DEBUG, subsystem, event)

fun PodderLogger.logInfo(subsystem: Subsystem, event: LogEvent) =
    log(LogLevel.INFO, subsystem, event)

fun PodderLogger.logWarn(subsystem: Subsystem, event: LogEvent, throwable: Throwable? = null) =
    log(LogLevel.WARN, subsystem, event, throwable)

fun PodderLogger.logError(subsystem: Subsystem, event: LogEvent, throwable: Throwable? = null) =
    log(LogLevel.ERROR, subsystem, event, throwable)

object NoOpLogger : PodderLogger {
    override fun log(level: LogLevel, subsystem: Subsystem, event: LogEvent, throwable: Throwable?) = Unit
}

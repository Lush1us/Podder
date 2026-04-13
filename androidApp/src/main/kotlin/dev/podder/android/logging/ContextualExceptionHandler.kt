package com.lush1us.podder.logging

/**
 * Delegating UncaughtExceptionHandler.
 *
 * Harvests crash context → persists atomically → delegates to the previously
 * registered handler (e.g. system default or Crashlytics) so existing crash
 * pipelines are unaffected.
 */
class ContextualExceptionHandler(
    private val previous: Thread.UncaughtExceptionHandler?,
    private val harvester: CrashContextHarvester,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val payload = harvester.harvest(thread, throwable)
            harvester.persistToDisk(payload)
        } catch (_: Exception) {
            // Never crash the crash handler
        } finally {
            previous?.uncaughtException(thread, throwable) ?: System.exit(2)
        }
    }

    companion object {
        fun install(harvester: CrashContextHarvester) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(
                ContextualExceptionHandler(previous, harvester)
            )
        }
    }
}

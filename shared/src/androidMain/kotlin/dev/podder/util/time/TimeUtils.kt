package dev.podder.util.time

actual fun platformNowUtcEpoch(): Long = System.currentTimeMillis() / 1000

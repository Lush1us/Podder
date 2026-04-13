package dev.podder.data.discovery

internal actual fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1000L

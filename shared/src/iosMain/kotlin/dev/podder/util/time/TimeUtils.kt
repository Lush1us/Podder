package dev.podder.util.time

import platform.Foundation.NSDate

actual fun platformNowUtcEpoch(): Long = NSDate.date().timeIntervalSince1970.toLong()

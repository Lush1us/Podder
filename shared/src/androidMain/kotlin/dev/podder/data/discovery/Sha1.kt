package dev.podder.data.discovery

import java.security.MessageDigest

internal actual fun sha1Hex(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}

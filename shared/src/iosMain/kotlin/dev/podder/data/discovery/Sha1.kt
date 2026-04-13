package dev.podder.data.discovery

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.CoreCrypto.CC_SHA1
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH

@OptIn(ExperimentalForeignApi::class)
internal actual fun sha1Hex(input: String): String {
    val data = input.encodeToByteArray()
    return memScoped {
        val digest = allocArray<UByteVar>(CC_SHA1_DIGEST_LENGTH)
        CC_SHA1(data.refTo(0), data.size.toUInt(), digest)
        (0 until CC_SHA1_DIGEST_LENGTH).joinToString("") { "%02x".format(digest[it].toInt()) }
    }
}

package dev.podder.data.store

actual class KVStore actual constructor(path: String) {
    actual fun putLong(key: String, value: Long)    = Unit // TODO: wire via cinterop
    actual fun getLong(key: String, default: Long)  = default
    actual fun putFloat(key: String, value: Float)  = Unit
    actual fun getFloat(key: String, default: Float) = default
    actual fun putString(key: String, value: String) = Unit
    actual fun getString(key: String, default: String) = default
    actual fun putBool(key: String, value: Boolean) = Unit
    actual fun getBool(key: String, default: Boolean) = default
    actual fun close() = Unit
}

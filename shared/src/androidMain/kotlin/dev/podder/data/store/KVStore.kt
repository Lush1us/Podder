package dev.podder.data.store

actual class KVStore actual constructor(path: String) {
    private val handle: Long

    init {
        System.loadLibrary("kvstore")
        handle = nativeOpen(path)
    }

    actual fun putLong(key: String, value: Long)   { nativePutLong(handle, key, value) }
    actual fun getLong(key: String, default: Long)  = nativeGetLong(handle, key, default)
    actual fun putFloat(key: String, value: Float)  { nativePutFloat(handle, key, value) }
    actual fun getFloat(key: String, default: Float) = nativeGetFloat(handle, key, default)
    actual fun putString(key: String, value: String) { nativePutString(handle, key, value) }
    actual fun getString(key: String, default: String) = nativeGetString(handle, key, default)
    actual fun putBool(key: String, value: Boolean) { nativePutBool(handle, key, value) }
    actual fun getBool(key: String, default: Boolean) = nativeGetBool(handle, key, default)
    actual fun close() { nativeClose(handle) }

    private external fun nativeOpen(path: String): Long
    private external fun nativeClose(handle: Long)
    private external fun nativePutLong(handle: Long, key: String, value: Long)
    private external fun nativeGetLong(handle: Long, key: String, default: Long): Long
    private external fun nativePutFloat(handle: Long, key: String, value: Float)
    private external fun nativeGetFloat(handle: Long, key: String, default: Float): Float
    private external fun nativePutString(handle: Long, key: String, value: String)
    private external fun nativeGetString(handle: Long, key: String, default: String): String
    private external fun nativePutBool(handle: Long, key: String, value: Boolean)
    private external fun nativeGetBool(handle: Long, key: String, default: Boolean): Boolean
}

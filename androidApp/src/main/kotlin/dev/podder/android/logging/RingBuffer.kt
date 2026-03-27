package dev.podder.android.logging

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Fixed-capacity circular string buffer. Thread-safe via ReentrantLock (not Mutex) so
 * it can be read synchronously inside an UncaughtExceptionHandler without coroutine context.
 */
internal class RingBuffer(private val capacity: Int = 500) {

    private val buffer = arrayOfNulls<String>(capacity)
    private var head = 0   // index of oldest entry
    private var count = 0  // number of valid entries
    private val lock = ReentrantLock()

    fun append(line: String) {
        lock.withLock {
            val writeIndex = (head + count) % capacity
            buffer[writeIndex] = line
            if (count < capacity) {
                count++
            } else {
                // Buffer full — advance head to discard oldest
                head = (head + 1) % capacity
            }
        }
    }

    /** Returns a snapshot ordered oldest → newest. */
    fun snapshot(): List<String> {
        lock.withLock {
            val result = ArrayList<String>(count)
            for (i in 0 until count) {
                buffer[(head + i) % capacity]?.let { result.add(it) }
            }
            return result
        }
    }
}

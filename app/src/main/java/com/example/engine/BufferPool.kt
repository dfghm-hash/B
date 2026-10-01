package com.example.engine

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * High-performance reusable byte buffer pool.
 * Eliminates garbage collector thrashing and OutOfMemory errors during multi-gigabyte file streaming.
 */
class BufferPool(
    val bufferSize: Int,
    val maxPooledBuffers: Int
) {
    private val pool = ConcurrentLinkedQueue<ByteArray>()
    private val createdCount = AtomicInteger(0)

    /**
     * Acquires a byte array of size [bufferSize].
     * Reuses an idle buffer if available, or allocates a new one if below capacity.
     */
    fun acquire(): ByteArray {
        val existing = pool.poll()
        if (existing != null) {
            return existing
        }
        createdCount.incrementAndGet()
        return ByteArray(bufferSize)
    }

    /**
     * Returns a buffer to the pool for future reuse.
     */
    fun release(buffer: ByteArray) {
        if (buffer.size == bufferSize && pool.size < maxPooledBuffers) {
            pool.offer(buffer)
        }
    }

    /**
     * Empties the pool and releases all retained references for garbage collection.
     */
    fun clear() {
        pool.clear()
        createdCount.set(0)
    }

    val pooledCount: Int get() = pool.size
    val totalAllocatedCount: Int get() = createdCount.get()
}

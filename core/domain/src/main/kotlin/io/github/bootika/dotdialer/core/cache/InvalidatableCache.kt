package io.github.bootika.dotdialer.core.cache

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A small process-local cache which prevents duplicate concurrent provider reads.
 * Results that finish after an invalidation are returned to their caller but never stored.
 */
class InvalidatableCache<K : Any, V : Any> {
    private val values = ConcurrentHashMap<K, V>()
    private val version = AtomicLong(0L)
    private val loadMutex = Mutex()

    suspend fun getOrLoad(key: K, loader: suspend () -> V): V {
        values[key]?.let { return it }
        return loadMutex.withLock {
            values[key]?.let { return@withLock it }
            val loadVersion = version.get()
            val loaded = loader()
            if (version.get() == loadVersion) values[key] = loaded
            loaded
        }
    }

    fun invalidate() {
        version.incrementAndGet()
        values.clear()
    }

    fun size(): Int = values.size
}

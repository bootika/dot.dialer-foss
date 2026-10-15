package io.github.bootika.dotdialer.core.cache

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InvalidatableCacheTest {
    @Test
    fun `coalesces concurrent loads for the same key`() = runTest {
        val cache = InvalidatableCache<String, String>()
        val loadCount = AtomicInteger(0)

        val results = List(10) {
            async {
                cache.getOrLoad("contacts") {
                    loadCount.incrementAndGet()
                    "snapshot"
                }
            }
        }.awaitAll()

        assertEquals(List(10) { "snapshot" }, results)
        assertEquals(1, loadCount.get())
        assertEquals(1, cache.size())
    }

    @Test
    fun `does not cache a load that completed after invalidation`() = runTest {
        val cache = InvalidatableCache<String, String>()
        val loaderStarted = CompletableDeferred<Unit>()
        val releaseLoader = CompletableDeferred<Unit>()

        val stale = async {
            cache.getOrLoad("contacts") {
                loaderStarted.complete(Unit)
                releaseLoader.await()
                "stale"
            }
        }
        loaderStarted.await()
        cache.invalidate()
        releaseLoader.complete(Unit)

        assertEquals("stale", stale.await())
        assertEquals(0, cache.size())
        assertEquals("fresh", cache.getOrLoad("contacts") { "fresh" })
    }
}

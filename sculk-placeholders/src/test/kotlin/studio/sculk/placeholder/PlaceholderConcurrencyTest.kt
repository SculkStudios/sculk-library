package studio.sculk.placeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.execution.SculkScope
import studio.sculk.testkit.FakeExecutor
import studio.sculk.text.Text
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.hours

class PlaceholderConcurrencyTest {
    private val scope = SculkScope.root("plugin", FakeExecutor()) {}
    private val text = Text()

    @Test
    fun `renders on many region threads at once each see their own audience's value, and the cache stays bounded`() {
        val placeholders = PlaceholderRegistry(scope, cacheSize = CACHE_SIZE)
        val balance = placeholders.audience("shop:balance", Refresh.Every(1.hours)) { (audience as Viewer).name }
        val viewers = List(VIEWERS) { Viewer("player$it") }
        val wrong = ConcurrentLinkedQueue<String>()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(THREADS)
        repeat(THREADS) { thread ->
            pool.execute {
                start.await()
                repeat(RENDERS) { render ->
                    val viewer = viewers[(thread * RENDERS + render) % viewers.size]
                    if (render % INVALIDATE_EVERY == 0) balance.invalidate(viewer)
                    val shown = text.render("<shop_balance>", placeholders.resolver(viewer)).plain()
                    if (shown != viewer.name) wrong += "$viewer saw $shown"
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "renders finished")
        assertEquals(emptyList<String>(), wrong.toList())
        assertTrue(balance.values.size <= CACHE_SIZE, "kept ${balance.values.size} values, more than $CACHE_SIZE")
    }

    private companion object {
        const val THREADS = 8
        const val RENDERS = 2_000
        const val VIEWERS = 64
        const val CACHE_SIZE = 16
        const val INVALIDATE_EVERY = 7
    }
}

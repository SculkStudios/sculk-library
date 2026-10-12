package studio.sculk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ResourceScopeTest {
    @Test
    fun `resources close newest first`() {
        val order = mutableListOf<String>()
        val scope = ResourceScope.root("plugin")
        scope.onClose { order += "listener" }
        scope.onClose { order += "task" }
        scope.onClose { order += "menu" }
        scope.close()
        assertEquals(listOf("menu", "task", "listener"), order)
    }

    @Test
    fun `every resource closes even when one throws, and the first error surfaces with the rest suppressed`() {
        val closed = mutableListOf<String>()
        val scope = ResourceScope.root("plugin")
        scope.onClose { closed += "a" }
        scope.onClose { error("second") }
        scope.onClose { error("third") }
        val thrown = assertThrows<IllegalStateException> { scope.close() }
        assertEquals("third", thrown.message)
        assertEquals(listOf("second"), thrown.suppressed.map { it.message })
        assertEquals(listOf("a"), closed)
    }

    @Test
    fun `closing a parent closes its children, and a child closed early leaves its parent`() {
        val closed = mutableListOf<String>()
        val plugin = ResourceScope.root("plugin")
        val player = plugin.child("player")
        val session = player.child("menu")
        session.onClose { closed += "menu" }
        player.onClose { closed += "player" }

        session.close()
        assertEquals(listOf("menu"), closed)
        plugin.close()
        assertEquals(listOf("menu", "player"), closed)
        assertTrue(player.isClosed)
        assertEquals("plugin/player/menu", session.name)
    }

    @Test
    fun `a resource handed to a closed scope is closed at once and the call fails`() {
        val scope = ResourceScope.root("plugin").also { it.close() }
        var closed = false
        assertThrows<IllegalStateException> { scope.own(AutoCloseable { closed = true }) }
        assertTrue(closed)
    }

    @Test
    fun `closing twice runs nothing twice`() {
        val count = AtomicInteger()
        val scope = ResourceScope.root("plugin")
        scope.onClose { count.incrementAndGet() }
        scope.close()
        scope.close()
        assertEquals(1, count.get())
    }

    @Test
    fun `cancelling a close action removes it`() {
        var ran = false
        val scope = ResourceScope.root("plugin")
        scope.onClose { ran = true }.close()
        scope.close()
        assertFalse(ran)
    }

    @Test
    fun `concurrent registration and close never lose a resource`() {
        val scope = ResourceScope.root("plugin")
        val closed = AtomicInteger()
        val rejected = AtomicInteger()
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        repeat(2_000) {
            pool.execute {
                start.await()
                try {
                    scope.own(AutoCloseable { closed.incrementAndGet() })
                } catch (_: IllegalStateException) {
                    rejected.incrementAndGet()
                }
            }
        }
        start.countDown()
        scope.close()
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        assertEquals(2_000, closed.get())
        assertTrue(rejected.get() <= closed.get())
    }
}

package studio.sculk.platform

import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import studio.sculk.annotation.SculkInternal
import java.util.concurrent.atomic.AtomicInteger

@OptIn(SculkInternal::class)
class SculkEventBusTest {
    private lateinit var server: ServerMock
    private lateinit var events: SculkEventBus

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        events = SculkEventBus(MockBukkit.createMockPlugin())
    }

    @AfterEach
    fun tearDown() {
        events.close()
        MockBukkit.unmock()
    }

    @Test
    fun `a handler receives the event it registered for`() {
        val seen = AtomicInteger()
        events.listen(PlayerJoinEvent::class.java) { seen.incrementAndGet() }

        server.addPlayer()

        assertEquals(1, seen.get())
    }

    @Test
    fun `a handler does not receive an event of another type`() {
        val quits = AtomicInteger()
        events.listen(PlayerQuitEvent::class.java) { quits.incrementAndGet() }

        server.addPlayer()

        assertEquals(0, quits.get())
    }

    @Test
    fun `a filter that rejects stops the handler running`() {
        val seen = AtomicInteger()
        events.listen(PlayerJoinEvent::class.java, filter = { false }) { seen.incrementAndGet() }

        server.addPlayer()

        assertEquals(0, seen.get())
    }

    @Test
    fun `closing a handle unregisters only that listener`() {
        val kept = AtomicInteger()
        val dropped = AtomicInteger()
        events.listen(PlayerJoinEvent::class.java) { kept.incrementAndGet() }
        val handle = events.listen(PlayerJoinEvent::class.java) { dropped.incrementAndGet() }

        handle.close()
        server.addPlayer()

        assertEquals(1, kept.get())
        assertEquals(0, dropped.get())
    }

    @Test
    fun `closing the bus unregisters everything it owns`() {
        val seen = AtomicInteger()
        events.listen(PlayerJoinEvent::class.java) { seen.incrementAndGet() }

        events.close()
        server.addPlayer()

        assertEquals(0, seen.get(), "a closed bus must not keep delivering to a disabled plugin")
    }

    @Test
    fun `registering from inside a handler does not break delivery`() {
        // The listener list is copy-on-write precisely so this cannot throw a
        // ConcurrentModificationException part-way through a dispatch.
        val seen = AtomicInteger()
        events.listen(PlayerJoinEvent::class.java) {
            seen.incrementAndGet()
            events.listen(PlayerQuitEvent::class.java) { }
        }

        server.addPlayer()
        server.addPlayer()

        assertEquals(2, seen.get())
    }

    @Test
    fun `closing twice is not an error`() {
        events.listen(PlayerJoinEvent::class.java) { }

        events.close()
        events.close()

        assertTrue(true, "a second close during a failed start-up must not mask the real failure")
    }
}

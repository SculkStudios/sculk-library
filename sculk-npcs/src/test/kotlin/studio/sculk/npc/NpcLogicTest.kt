package studio.sculk.npc

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext

/** The parts of an NPC that need no server: click cooldowns, chunk tickets, head targets and skin lookups. */
class NpcLogicTest {
    private val ada = UUID.randomUUID()
    private val bob = UUID.randomUUID()

    @Test
    fun `a player's clicks inside the cooldown count once`() {
        val gate = ClickGate(cooldownNanos = 100)
        assertTrue(gate.pass(ada, now = 1_000))
        assertFalse(gate.pass(ada, now = 1_050))
        assertFalse(gate.pass(ada, now = 1_099))
        assertTrue(gate.pass(ada, now = 1_100))
    }

    @Test
    fun `players do not share a cooldown, and leaving forgets one`() {
        val gate = ClickGate(cooldownNanos = 100)
        assertTrue(gate.pass(ada, now = 0))
        assertTrue(gate.pass(bob, now = 10))
        gate.forget(ada)
        assertEquals(1, gate.size)
        assertTrue(gate.pass(ada, now = 20))
    }

    @Test
    fun `a zero cooldown counts every click and remembers nobody`() {
        val gate = ClickGate(cooldownNanos = 0)
        repeat(3) { assertTrue(gate.pass(ada, now = 5)) }
        assertEquals(0, gate.size)
    }

    @Test
    fun `only the first NPC in a chunk adds its ticket and only the last one out removes it`() {
        val tickets = ChunkTickets()
        val chunk = ChunkKey(UUID.randomUUID(), 3, -7)
        val other = chunk.copy(x = 4)
        assertTrue(tickets.acquire(chunk))
        assertFalse(tickets.acquire(chunk))
        assertTrue(tickets.acquire(other))
        assertFalse(tickets.release(chunk))
        assertTrue(tickets.release(chunk))
        assertEquals(0, tickets.count(chunk))
        assertFalse(tickets.release(chunk), "releasing a chunk nobody holds removes nothing")
        assertEquals(1, tickets.count(other))
    }

    @Test
    fun `the nearest player within range is the one an NPC looks at`() {
        val distances = mapOf("far" to 60.0, "near" to 9.0, "nearer" to 4.0, "other region" to null)
        assertEquals("nearer", nearestWithin(8.0, distances.keys) { distances[it] })
        assertEquals("near", nearestWithin(8.0, listOf("far", "near", "other region")) { distances[it] })
        assertNull(nearestWithin(1.5, distances.keys) { distances[it] })
        assertNull(nearestWithin(8.0, listOf("other region")) { distances[it] })
    }

    @Test
    fun `many NPCs wearing one player's skin look it up once`() = runTest {
        val lookups = AtomicInteger()
        val answer = CompletableDeferred<SkinTexture?>()
        val cache = SkinCache(this, EmptyCoroutineContext) {
            lookups.incrementAndGet()
            answer.await()
        }
        val first = async { cache.resolve(ada) }
        val second = async { cache.resolve(ada) }
        // Let both callers reach the lookup before it answers.
        while (lookups.get() == 0) yield()
        val texture = SkinTexture("dGV4dHVyZXM=", null)
        answer.complete(texture)
        assertSame(texture, first.await())
        assertSame(texture, second.await())
        assertSame(texture, cache.resolve(ada))
        assertSame(texture, cache.known(Skin.player(ada)))
        assertEquals(1, lookups.get())
    }

    @Test
    fun `a lookup that finds nothing is asked again for the next NPC`() = runTest {
        val lookups = AtomicInteger()
        val cache = SkinCache(this, EmptyCoroutineContext) {
            if (lookups.incrementAndGet() == 1) null else SkinTexture("dGV4dHVyZXM=", null)
        }
        assertNull(cache.resolve(bob))
        assertNull(cache.known(Skin.player(bob)))
        assertEquals("dGV4dHVyZXM=", cache.resolve(bob)?.value)
        assertEquals(2, lookups.get())
    }

    @Test
    fun `textures and the default skin need no lookup`() = runTest {
        val cache = SkinCache(this, EmptyCoroutineContext) { error("no lookup expected") }
        assertNull(cache.known(Skin.Default))
        assertEquals("dGV4dHVyZXM=", cache.known(Skin.Textures("dGV4dHVyZXM=", null))?.value)
    }
}

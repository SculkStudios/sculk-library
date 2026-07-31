package studio.sculk.visual

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import studio.sculk.annotation.SculkInternal
import studio.sculk.packets.FakeVirtualEntityService
import studio.sculk.scheduler.FakeScheduler
import studio.sculk.text.SculkMessages
import java.util.UUID

@OptIn(SculkInternal::class)
class HologramServiceTest {
    private val world: World = mock()
    private val entities = FakeVirtualEntityService()
    private val scheduler = FakeScheduler()
    private val players = mutableListOf<Player>()

    private fun service(intervalTicks: Long = 10) = HologramService(
        entities = entities,
        messages = SculkMessages(),
        scheduler = scheduler,
        reconcileIntervalTicks = intervalTicks,
        onlinePlayers = { players },
        viewerOf = { uuid -> players.firstOrNull { it.uniqueId == uuid } },
    )

    private fun player(x: Double, z: Double): Player {
        val id = UUID.randomUUID()
        val player: Player = mock()
        whenever(player.uniqueId).thenReturn(id)
        whenever(player.world).thenReturn(world)
        whenever(player.location).thenReturn(location(x, z))
        return player.also { players += it }
    }

    private fun location(x: Double, z: Double): Location = Location(world, x, 64.0, z)

    @Test
    fun `a hologram is shown only to players inside its range`() {
        val service = service()
        service.create(location(0.0, 0.0), listOf("hi"), HologramOptions(viewRangeBlocks = 16.0))

        val near = player(5.0, 0.0)
        val far = player(200.0, 0.0)

        scheduler.advance(10)

        assertEquals(1, entities.countFor(near), "the nearby player is sent a spawn")
        assertEquals(0, entities.countFor(far), "the distant player is sent nothing")
    }

    @Test
    fun `an unchanged hologram sends nothing on a later reconcile`() {
        val service = service()
        service.create(location(0.0, 0.0), listOf("hi"))
        player(1.0, 0.0)

        scheduler.advance(10)
        val afterSpawn = entities.sent.size
        assertTrue(afterSpawn > 0)

        scheduler.advance(50)

        assertEquals(afterSpawn, entities.sent.size, "a hologram nobody edited must cost nothing to keep")
    }

    @Test
    fun `changing the lines sends exactly one update per viewer`() {
        val service = service()
        val hologram = service.create(location(0.0, 0.0), listOf("before"))
        player(1.0, 0.0)
        scheduler.advance(10)
        entities.clear()

        hologram.setLines(listOf("after"))
        scheduler.advance(10)

        val updates = entities.sent.filterIsInstance<FakeVirtualEntityService.Sent.Update>()
        assertEquals(1, updates.size)

        entities.clear()
        scheduler.advance(10)
        assertEquals(0, entities.sent.size, "the dirty flag must clear after one send")
    }

    @Test
    fun `setting the same lines again is not a change`() {
        val service = service()
        val hologram = service.create(location(0.0, 0.0), listOf("same"))
        player(1.0, 0.0)
        scheduler.advance(10)
        entities.clear()

        hologram.setLines(listOf("same"))
        scheduler.advance(10)

        assertEquals(0, entities.sent.size)
    }

    @Test
    fun `a player who walks out of range is sent a despawn`() {
        val service = service()
        service.create(location(0.0, 0.0), listOf("hi"), HologramOptions(viewRangeBlocks = 16.0))
        val walker = player(1.0, 0.0)
        scheduler.advance(10)
        entities.clear()

        whenever(walker.location).thenReturn(location(500.0, 0.0))
        scheduler.advance(10)

        assertEquals(1, entities.sent.filterIsInstance<FakeVirtualEntityService.Sent.Despawn>().size)
    }

    @Test
    fun `moving a hologram across a chunk boundary keeps it findable`() {
        val service = service()
        val hologram = service.create(location(0.0, 0.0), listOf("hi"), HologramOptions(viewRangeBlocks = 64.0))
        val watcher = player(0.0, 0.0)
        scheduler.advance(10)
        entities.clear()

        // Two chunks east — a different bucket. If re-indexing were wrong the reconcile would
        // stop finding it and the viewer would never be updated.
        hologram.teleport(location(40.0, 0.0))
        whenever(watcher.location).thenReturn(location(40.0, 0.0))
        scheduler.advance(10)

        assertTrue(
            entities.sent.any { it is FakeVirtualEntityService.Sent.Teleport },
            "expected a teleport after re-bucketing, got ${entities.sent}",
        )
    }

    @Test
    fun `removing a hologram despawns it and stops tracking it`() {
        val service = service()
        val hologram = service.create(location(0.0, 0.0), listOf("hi"))
        player(1.0, 0.0)
        scheduler.advance(10)
        entities.clear()

        hologram.remove()

        assertEquals(0, service.count)
        assertEquals(1, entities.sent.filterIsInstance<FakeVirtualEntityService.Sent.Despawn>().size)
    }

    @Test
    fun `closing the service despawns everything and stops the task`() {
        val service = service()
        service.create(location(0.0, 0.0), listOf("a"))
        service.create(location(1.0, 0.0), listOf("b"))
        player(0.0, 0.0)
        scheduler.advance(10)

        service.close()

        assertEquals(0, service.count)
        assertEquals(0, scheduler.pending.size, "the reconcile task must be cancelled")
    }

    @Test
    fun `nothing is sent when the backend is unavailable`() {
        entities.available = false
        val service = service()
        service.create(location(0.0, 0.0), listOf("hi"))
        player(0.0, 0.0)

        scheduler.advance(10)

        assertEquals(0, entities.sent.size)
        assertEquals(false, service.available)
    }
}

package studio.sculk.packets

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.scheduler.SculkScheduler

class ClientBlockDigTest {
    @Test
    fun `intercept cancels, runs the block, then acknowledges`() {
        val order = mutableListOf<String>()
        val context = context(order = order)

        context.intercept { order += "work" }

        assertEquals(listOf("cancel", "work", "acknowledge"), order)
        assertTrue(context.cancelled)
        assertTrue(context.acknowledged)
    }

    @Test
    fun `cancel and acknowledge are idempotent`() {
        val order = mutableListOf<String>()
        val context = context(order = order)

        repeat(3) {
            context.cancel()
            context.acknowledge()
        }

        assertEquals(listOf("cancel", "acknowledge"), order)
    }

    @Test
    fun `a negative sequence carries no acknowledgement debt`() {
        val order = mutableListOf<String>()
        val context = context(sequence = -1, order = order)

        context.intercept { order += "work" }

        assertEquals(listOf("cancel", "work"), order)
        assertFalse(context.acknowledged)
    }

    @Test
    fun `dig support is absent until a backend supplies it`() {
        val service = TestPacketService(blocks = null)

        val dig = service.clientBlocks.onDig { }
        val use = service.clientBlocks.onUse { }
        val ack = service.clientBlocks.acknowledge(mock(), 1)

        assertTrue(dig is SculkResult.Failure)
        assertTrue(use is SculkResult.Failure)
        assertTrue(ack is SculkResult.Failure)
    }

    @Test
    fun `dig listeners reach the backend when one is present`() {
        val backend = RecordingBackend()
        val service = TestPacketService(backend)

        assertTrue(service.clientBlocks.onDig(PacketPriority.Highest) { } is SculkResult.Success)
        assertTrue(service.clientBlocks.acknowledge(mock(), 7) is SculkResult.Success)

        assertTrue(service.clientBlocks.onUse(PacketPriority.Low) { } is SculkResult.Success)

        assertEquals(listOf(PacketPriority.Highest), backend.priorities)
        assertEquals(listOf(PacketPriority.Low), backend.usePriorities)
        assertEquals(listOf(7), backend.acknowledged)
    }

    private fun context(sequence: Int = 42, order: MutableList<String>) = BlockDigContext(
        player = mock(),
        world = mock(),
        x = 1,
        y = 2,
        z = 3,
        action = BlockDigAction.Start,
        sequence = sequence,
        scheduler = ImmediateScheduler(),
        cancelAction = { order += "cancel" },
        acknowledgeAction = { order += "acknowledge" },
    )

    private class RecordingBackend : ClientBlockBackend {
        val priorities: MutableList<PacketPriority> = mutableListOf()
        val usePriorities: MutableList<PacketPriority> = mutableListOf()
        val acknowledged: MutableList<Int> = mutableListOf()

        override fun acknowledge(player: Player, sequence: Int): SculkResult<Unit> {
            acknowledged += sequence
            return SculkResult.success(Unit)
        }

        override fun listenDig(priority: PacketPriority, handler: BlockDigContext.() -> Unit): SculkResult<SculkHandle> {
            priorities += priority
            return SculkResult.success(SculkHandle {})
        }

        override fun listenUse(priority: PacketPriority, handler: BlockUseContext.() -> Unit): SculkResult<SculkHandle> {
            usePriorities += priority
            return SculkResult.success(SculkHandle {})
        }
    }

    private class TestPacketService(private val blocks: ClientBlockBackend?) :
        AbstractPacketService(PacketBackend.PacketEvents, ImmediateScheduler()) {
        override fun clientBlockBackend(): ClientBlockBackend? = blocks

        override fun listen(
            direction: PacketDirection,
            type: PacketKey,
            priority: PacketPriority,
            handler: PacketContext.() -> Unit,
        ): SculkResult<SculkHandle> = SculkResult.success(SculkHandle {})

        override fun send(player: Player, packet: SculkPacket): SculkResult<Unit> = SculkResult.success(Unit)
    }

    private class ImmediateScheduler : SculkScheduler {
        override fun runSync(task: Runnable): SculkHandle {
            task.run()
            return SculkHandle {}
        }

        override fun runSyncDelayed(delayTicks: Long, task: Runnable): SculkHandle = runSync(task)

        override fun runSyncRepeating(delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle = runSync(task)

        override fun runSync(entity: Entity, task: Runnable): SculkHandle = runSync(task)

        override fun runSync(location: Location, task: Runnable): SculkHandle = runSync(task)

        override fun runAsync(task: Runnable): SculkHandle = runSync(task)

        override fun runAsyncDelayed(delayTicks: Long, task: Runnable): SculkHandle = runAsync(task)

        override fun runAsyncRepeating(delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle = runAsync(task)
    }
}

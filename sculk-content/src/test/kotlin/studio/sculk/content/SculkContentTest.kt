package studio.sculk.content

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.core.SculkHandle
import studio.sculk.core.SculkResult
import studio.sculk.core.scheduler.SculkScheduler
import studio.sculk.packets.AbstractPacketService
import studio.sculk.packets.PacketBackend
import studio.sculk.packets.PacketContext
import studio.sculk.packets.PacketDirection
import studio.sculk.packets.PacketKey
import studio.sculk.packets.PacketPriority
import studio.sculk.packets.SculkPacket

class SculkContentTest {
    @Test
    fun `content exposes only implemented client block facade`() {
        val service = TestPacketService()
        val content = service.content

        val fieldNames =
            SculkContent::class.java
                .declaredFields
                .map { it.name }
                .toSet()
        assertTrue("clientBlocks" in fieldNames)
        assertNotNull(content.clientBlocks)
        assertFalse("holograms" in fieldNames)
        assertFalse("fakeEntities" in fieldNames)
        assertFalse("nametags" in fieldNames)
    }

    private class TestPacketService : AbstractPacketService(PacketBackend.ProtocolLib, ImmediateScheduler()) {
        override fun listen(
            direction: PacketDirection,
            type: PacketKey,
            priority: PacketPriority,
            handler: PacketContext.() -> Unit,
        ): SculkResult<SculkHandle> = SculkResult.success(SculkHandle {})

        override fun send(
            player: Player,
            packet: SculkPacket,
        ): SculkResult<Unit> = SculkResult.success(Unit)
    }

    private class ImmediateScheduler : SculkScheduler {
        override fun runSync(task: Runnable): SculkHandle {
            task.run()
            return SculkHandle {}
        }

        override fun runSyncDelayed(
            delayTicks: Long,
            task: Runnable,
        ): SculkHandle = runSync(task)

        override fun runSyncRepeating(
            delayTicks: Long,
            periodTicks: Long,
            task: Runnable,
        ): SculkHandle = runSync(task)

        override fun runSync(
            entity: Entity,
            task: Runnable,
        ): SculkHandle = runSync(task)

        override fun runSync(
            location: Location,
            task: Runnable,
        ): SculkHandle = runSync(task)

        override fun runAsync(task: Runnable): SculkHandle = runSync(task)

        override fun runAsyncDelayed(
            delayTicks: Long,
            task: Runnable,
        ): SculkHandle = runAsync(task)

        override fun runAsyncRepeating(
            delayTicks: Long,
            periodTicks: Long,
            task: Runnable,
        ): SculkHandle = runAsync(task)
    }
}

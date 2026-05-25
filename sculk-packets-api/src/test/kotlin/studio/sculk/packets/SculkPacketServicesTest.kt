package studio.sculk.packets

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import studio.sculk.core.SculkHandle
import studio.sculk.core.SculkResult
import studio.sculk.core.annotation.SculkInternal
import studio.sculk.core.scheduler.SculkScheduler

@OptIn(SculkInternal::class)
class SculkPacketServicesTest {
    @Test
    fun `auto mode prefers available PacketEvents provider`() {
        val result =
            SculkPacketServices.create(
                plugin = mock(),
                scheduler = ImmediateScheduler(),
                config = PacketServiceConfig(),
                providers =
                    listOf(
                        TestProvider(PacketBackend.ProtocolLib, available = true),
                        TestProvider(PacketBackend.PacketEvents, available = true),
                    ),
            )

        assertTrue(result is SculkResult.Success)
        assertEquals(PacketBackend.PacketEvents, (result as SculkResult.Success).value.backend)
    }

    @Test
    fun `explicit backend mode ignores other available providers`() {
        val config = PacketServiceConfig().apply { backend = PacketBackendMode.ProtocolLib }

        val result =
            SculkPacketServices.create(
                plugin = mock(),
                scheduler = ImmediateScheduler(),
                config = config,
                providers =
                    listOf(
                        TestProvider(PacketBackend.PacketEvents, available = true),
                        TestProvider(PacketBackend.ProtocolLib, available = true),
                    ),
            )

        assertTrue(result is SculkResult.Success)
        assertEquals(PacketBackend.ProtocolLib, (result as SculkResult.Success).value.backend)
    }

    @Test
    fun `missing backend returns failure with install guidance`() {
        val result =
            SculkPacketServices.create(
                plugin = mock(),
                scheduler = ImmediateScheduler(),
                config = PacketServiceConfig(),
                providers = emptyList(),
            )

        assertTrue(result is SculkResult.Failure)
        assertTrue((result as SculkResult.Failure).message.contains("packet adapter module"))
    }

    @Test
    fun `disabled backend mode returns explicit failure`() {
        val config = PacketServiceConfig().apply { backend = PacketBackendMode.Disabled }

        val result =
            SculkPacketServices.create(
                plugin = mock(),
                scheduler = ImmediateScheduler(),
                config = config,
                providers = listOf(TestProvider(PacketBackend.PacketEvents, available = true)),
            )

        assertTrue(result is SculkResult.Failure)
        assertEquals("Packet subsystem is disabled.", (result as SculkResult.Failure).message)
    }

    private class TestProvider(
        override val backend: PacketBackend,
        private val available: Boolean,
    ) : SculkPacketServiceProvider {
        override fun isAvailable(): Boolean = available

        override fun create(
            plugin: JavaPlugin,
            scheduler: SculkScheduler,
        ): SculkPacketService = TestPacketService(backend, scheduler)
    }

    private class TestPacketService(
        backend: PacketBackend,
        scheduler: SculkScheduler,
    ) : AbstractPacketService(backend, scheduler) {
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

package studio.sculk.platform

import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import studio.sculk.core.SculkHandle
import studio.sculk.core.SculkResult
import studio.sculk.core.scheduler.SculkScheduler
import studio.sculk.packets.SculkPacketService
import studio.sculk.platform.command.SculkCommandBridge
import studio.sculk.platform.event.SculkEventBus

class SculkPlatformCloseTest {
    @Test
    fun `platform closes owned handles in reverse order once`() {
        val closed = mutableListOf<Int>()
        val platform =
            platform(
                handles =
                    listOf(
                        SculkHandle { closed += 1 },
                        SculkHandle { closed += 2 },
                        SculkHandle { closed += 3 },
                    ),
            )

        platform.close()
        platform.close()

        assertEquals(listOf(3, 2, 1), closed)
    }

    @Test
    fun `disabled subsystem access fails clearly`() {
        val platform = platform()

        val error = assertThrows(IllegalStateException::class.java) { platform.config }

        assertEquals(true, error.message!!.contains("config()"))
    }

    @Test
    fun `packet accessor surfaces backend startup failure`() {
        val platform =
            platform(
                packetResult = SculkResult.failure("No available packet backend found for PacketEvents."),
            )

        val error = assertThrows(IllegalStateException::class.java) { platform.packets }

        assertEquals(true, error.message!!.contains("PacketEvents"))
    }

    @Test
    fun `packet result exposes optional backend failure without throwing`() {
        val failure = SculkResult.failure("No available packet backend found for PacketEvents.")
        val platform = platform(packetResult = failure)

        assertEquals(failure, platform.packetsResult)
    }

    private fun platform(
        handles: List<SculkHandle> = emptyList(),
        packetResult: SculkResult<SculkPacketService>? = null,
    ): SculkPlatform =
        SculkPlatform(
            plugin = mock<JavaPlugin>(),
            scheduler = mock<SculkScheduler>(),
            events = mock<SculkEventBus>(),
            commands = mock<SculkCommandBridge>(),
            configService = null,
            dataService = null,
            integrationService = null,
            packetServiceResult = packetResult,
            handles = handles,
        )
}

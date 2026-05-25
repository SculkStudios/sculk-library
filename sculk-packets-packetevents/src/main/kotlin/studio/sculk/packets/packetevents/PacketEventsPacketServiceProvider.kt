package studio.sculk.packets.packetevents

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
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
import studio.sculk.packets.SculkPacketService
import studio.sculk.packets.SculkPacketServiceProvider

public class PacketEventsPacketServiceProvider : SculkPacketServiceProvider {
    override val backend: PacketBackend = PacketBackend.PacketEvents

    override fun isAvailable(): Boolean = classExists("com.github.retrooper.packetevents.PacketEvents")

    override fun create(
        plugin: JavaPlugin,
        scheduler: SculkScheduler,
    ): SculkPacketService = PacketEventsPacketService(plugin, scheduler)

    private fun classExists(name: String): Boolean =
        runCatching {
            Class.forName(name, false, javaClass.classLoader)
        }.isSuccess
}

private class PacketEventsPacketService(
    private val plugin: JavaPlugin,
    scheduler: SculkScheduler,
) : AbstractPacketService(PacketBackend.PacketEvents, scheduler) {
    private val handles = mutableListOf<SculkHandle>()

    override fun listen(
        direction: PacketDirection,
        type: PacketKey,
        priority: PacketPriority,
        handler: PacketContext.() -> Unit,
    ): SculkResult<SculkHandle> =
        SculkResult.failure(
            "PacketEvents backend detected for ${plugin.name}, but low-level packet listener binding is not enabled in this lightweight adapter yet.",
        )

    override fun send(
        player: Player,
        packet: SculkPacket,
    ): SculkResult<Unit> =
        SculkResult.failure(
            "PacketEvents backend detected for ${plugin.name}, but packet sending requires a backend-specific packet wrapper.",
        )

    override fun close() {
        handles.asReversed().forEach { it.close() }
        handles.clear()
    }
}

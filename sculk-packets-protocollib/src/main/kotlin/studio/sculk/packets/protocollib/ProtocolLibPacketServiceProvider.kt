package studio.sculk.packets.protocollib

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.ListenerPriority
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.events.PacketEvent
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.packets.AbstractPacketService
import studio.sculk.packets.PacketBackend
import studio.sculk.packets.PacketContext
import studio.sculk.packets.PacketDirection
import studio.sculk.packets.PacketKey
import studio.sculk.packets.PacketPriority
import studio.sculk.packets.SculkPacket
import studio.sculk.packets.SculkPacketService
import studio.sculk.packets.SculkPacketServiceProvider
import studio.sculk.scheduler.SculkScheduler

public class ProtocolLibPacket(
    public val container: PacketContainer,
    override val direction: PacketDirection,
    override val type: PacketKey,
) : SculkPacket

public class ProtocolLibPacketServiceProvider : SculkPacketServiceProvider {
    override val backend: PacketBackend = PacketBackend.ProtocolLib

    override fun isAvailable(): Boolean = classExists("com.comphenix.protocol.ProtocolLibrary")

    override fun create(plugin: JavaPlugin, scheduler: SculkScheduler): SculkPacketService = ProtocolLibPacketService(plugin, scheduler)

    private fun classExists(name: String): Boolean = runCatching {
        Class.forName(name, false, javaClass.classLoader)
    }.isSuccess
}

private class ProtocolLibPacketService(private val plugin: JavaPlugin, scheduler: SculkScheduler) :
    AbstractPacketService(PacketBackend.ProtocolLib, scheduler) {
    private val handles = mutableListOf<SculkHandle>()

    override fun listen(
        direction: PacketDirection,
        type: PacketKey,
        priority: PacketPriority,
        handler: PacketContext.() -> Unit,
    ): SculkResult<SculkHandle> {
        val packetType =
            resolvePacketType(direction, type)
                ?: return SculkResult.failure("ProtocolLib could not resolve packet type $type for $direction.")

        val listener =
            object : PacketAdapter(plugin, priority.toProtocolLib(), listOf(packetType)) {
                override fun onPacketReceiving(event: PacketEvent) {
                    if (direction == PacketDirection.Serverbound) {
                        event.toContext(direction, type).handler()
                    }
                }

                override fun onPacketSending(event: PacketEvent) {
                    if (direction == PacketDirection.Clientbound) {
                        event.toContext(direction, type).handler()
                    }
                }
            }

        ProtocolLibrary.getProtocolManager().addPacketListener(listener)
        val handle =
            SculkHandle {
                ProtocolLibrary.getProtocolManager().removePacketListener(listener)
            }
        handles += handle
        return SculkResult.success(handle)
    }

    override fun send(player: Player, packet: SculkPacket): SculkResult<Unit> {
        if (packet !is ProtocolLibPacket) {
            return SculkResult.failure("ProtocolLib sending requires ProtocolLibPacket.")
        }
        return runCatching {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet.container, false)
        }.fold(
            onSuccess = { SculkResult.success(Unit) },
            onFailure = { SculkResult.failure("ProtocolLib failed to send packet ${packet.type}.", it) },
        )
    }

    override fun close() {
        handles.asReversed().forEach { it.close() }
        handles.clear()
    }

    private fun PacketEvent.toContext(direction: PacketDirection, type: PacketKey): PacketContext = PacketContext(
        player = player,
        direction = direction,
        type = type,
        scheduler = scheduler,
        cancelAction = { isCancelled = true },
        markChangedAction = {},
    )

    private fun resolvePacketType(direction: PacketDirection, key: PacketKey): PacketType? {
        val sender =
            when (direction) {
                PacketDirection.Clientbound -> PacketType.Sender.SERVER
                PacketDirection.Serverbound -> PacketType.Sender.CLIENT
            }

        val names =
            listOf(
                key.value,
                key.value.uppercase(),
                key.value
                    .uppercase()
                    .replace('.', '_')
                    .replace('-', '_'),
            ).distinct()

        return names.firstNotNullOfOrNull { name ->
            runCatching {
                PacketType.findCurrent(PacketType.Protocol.PLAY, sender, name)
            }.getOrNull()
        }
    }

    private fun PacketPriority.toProtocolLib(): ListenerPriority = when (this) {
        PacketPriority.Lowest -> ListenerPriority.LOWEST
        PacketPriority.Low -> ListenerPriority.LOW
        PacketPriority.Normal -> ListenerPriority.NORMAL
        PacketPriority.High -> ListenerPriority.HIGH
        PacketPriority.Highest -> ListenerPriority.HIGHEST
        PacketPriority.Monitor -> ListenerPriority.MONITOR
    }
}

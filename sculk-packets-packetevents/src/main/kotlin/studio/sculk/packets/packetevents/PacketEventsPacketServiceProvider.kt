package studio.sculk.packets.packetevents

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerCommon
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.event.ProtocolPacketEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon
import com.github.retrooper.packetevents.wrapper.PacketWrapper
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

public class PacketEventsPacket(
    public val wrapper: PacketWrapper<*>,
    override val direction: PacketDirection,
    override val type: PacketKey,
) : SculkPacket

public class PacketEventsPacketServiceProvider : SculkPacketServiceProvider {
    override val backend: PacketBackend = PacketBackend.PacketEvents

    override fun isAvailable(): Boolean = classExists("com.github.retrooper.packetevents.PacketEvents") &&
        runCatching { PacketEvents.getAPI() != null }.getOrDefault(false)

    override fun create(plugin: JavaPlugin, scheduler: SculkScheduler): SculkPacketService = PacketEventsPacketService(plugin, scheduler)

    private fun classExists(name: String): Boolean = runCatching {
        Class.forName(name, false, javaClass.classLoader)
    }.isSuccess
}

private class PacketEventsPacketService(private val plugin: JavaPlugin, scheduler: SculkScheduler) :
    AbstractPacketService(PacketBackend.PacketEvents, scheduler) {
    private val handles = mutableListOf<SculkHandle>()

    override fun listen(
        direction: PacketDirection,
        type: PacketKey,
        priority: PacketPriority,
        handler: PacketContext.() -> Unit,
    ): SculkResult<SculkHandle> {
        val packetType =
            resolvePacketType(direction, type)
                ?: return SculkResult.failure("PacketEvents could not resolve packet type $type for $direction.")

        val listener =
            object : PacketListenerAbstract(priority.toPacketEvents()) {
                override fun onPacketReceive(event: PacketReceiveEvent) {
                    if (direction == PacketDirection.Serverbound && event.packetType == packetType) {
                        event.toContext(direction, type).handler()
                    }
                }

                override fun onPacketSend(event: PacketSendEvent) {
                    if (direction == PacketDirection.Clientbound && event.packetType == packetType) {
                        event.toContext(direction, type).handler()
                    }
                }
            }

        return runCatching {
            PacketEvents.getAPI().eventManager.registerListener(listener)
        }.fold(
            onSuccess = { registered ->
                val handle =
                    SculkHandle {
                        unregister(registered)
                    }
                handles += handle
                SculkResult.success(handle)
            },
            onFailure = { SculkResult.failure("PacketEvents failed to register $type for ${plugin.name}.", it) },
        )
    }

    override fun send(player: Player, packet: SculkPacket): SculkResult<Unit> {
        if (packet !is PacketEventsPacket) {
            return SculkResult.failure("PacketEvents sending requires PacketEventsPacket.")
        }
        return runCatching {
            PacketEvents.getAPI().playerManager.sendPacket(player, packet.wrapper)
        }.fold(
            onSuccess = { SculkResult.success(Unit) },
            onFailure = { SculkResult.failure("PacketEvents failed to send packet ${packet.type}.", it) },
        )
    }

    override fun close() {
        handles.asReversed().forEach { it.close() }
        handles.clear()
    }

    private fun unregister(listener: PacketListenerCommon) {
        runCatching { PacketEvents.getAPI().eventManager.unregisterListener(listener) }
    }

    private fun ProtocolPacketEvent.toContext(direction: PacketDirection, type: PacketKey): PacketContext = PacketContext(
        player = runCatching { getPlayer<Player>() }.getOrNull(),
        direction = direction,
        type = type,
        scheduler = scheduler,
        cancelAction = { isCancelled = true },
        markChangedAction = { markForReEncode(true) },
    )

    private fun resolvePacketType(direction: PacketDirection, key: PacketKey): PacketTypeCommon? {
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
                when (direction) {
                    PacketDirection.Clientbound -> PacketType.Play.Server.valueOf(name)
                    PacketDirection.Serverbound -> PacketType.Play.Client.valueOf(name)
                }
            }.getOrNull()
        }
    }

    private fun PacketPriority.toPacketEvents(): PacketListenerPriority = when (this) {
        PacketPriority.Lowest -> PacketListenerPriority.LOWEST
        PacketPriority.Low -> PacketListenerPriority.LOW
        PacketPriority.Normal -> PacketListenerPriority.NORMAL
        PacketPriority.High -> PacketListenerPriority.HIGH
        PacketPriority.Highest -> PacketListenerPriority.HIGHEST
        PacketPriority.Monitor -> PacketListenerPriority.MONITOR
    }
}

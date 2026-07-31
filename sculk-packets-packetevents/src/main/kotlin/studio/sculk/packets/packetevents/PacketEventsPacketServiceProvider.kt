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
import com.github.retrooper.packetevents.protocol.player.DiggingAction
import com.github.retrooper.packetevents.protocol.player.InteractionHand
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.packets.AbstractPacketService
import studio.sculk.packets.BlockDigAction
import studio.sculk.packets.BlockDigContext
import studio.sculk.packets.BlockUseContext
import studio.sculk.packets.ClientBlockBackend
import studio.sculk.packets.PacketBackend
import studio.sculk.packets.PacketContext
import studio.sculk.packets.PacketDirection
import studio.sculk.packets.PacketGuard
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

/**
 * The live PacketEvents event behind a [PacketContext], for listeners that need to read or
 * rewrite fields Sculk's high-level services do not cover.
 *
 * ```kotlin
 * val raw = context.packetAs(PacketEventsEvent::class.java)?.event as? PacketReceiveEvent
 * val dig = raw?.let(::WrapperPlayClientPlayerDigging)
 * ```
 *
 * Valid only for the duration of the callback; PacketEvents recycles the event afterwards.
 */
public class PacketEventsEvent(
    public val event: ProtocolPacketEvent,
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

/**
 * `ClientboundBlockChangedAck`, which PacketEvents has no wrapper for. One varint: the client's
 * dig sequence, which it uses to retire the block prediction it made for that action.
 */
private class BlockChangedAckWrapper(private val sequence: Int) :
    PacketWrapper<BlockChangedAckWrapper>(PacketType.Play.Server.ACKNOWLEDGE_BLOCK_CHANGES) {
    override fun write() {
        writeVarInt(sequence)
    }
}

private class PacketEventsPacketService(private val plugin: JavaPlugin, scheduler: SculkScheduler) :
    AbstractPacketService(PacketBackend.PacketEvents, scheduler) {
    private val handles = mutableListOf<SculkHandle>()

    // Every handler goes through this. PacketEvents treats an exception escaping a listener as a
    // malformed packet and kicks the player, so a bug in a plugin handler would otherwise surface
    // as mass disconnects with a protocol error rather than as a stack trace.
    private val guard = PacketGuard(plugin.logger)

    override fun clientBlockBackend(): ClientBlockBackend = object : ClientBlockBackend {
        override fun acknowledge(player: Player, sequence: Int): SculkResult<Unit> {
            if (sequence < 0) return SculkResult.success(Unit)
            return runCatching {
                PacketEvents.getAPI().playerManager.sendPacket(player, BlockChangedAckWrapper(sequence))
            }.fold(
                onSuccess = { SculkResult.success(Unit) },
                onFailure = { SculkResult.failure("PacketEvents failed to acknowledge block change $sequence.", it) },
            )
        }

        override fun listenDig(priority: PacketPriority, handler: BlockDigContext.() -> Unit): SculkResult<SculkHandle> {
            val listener =
                object : PacketListenerAbstract(priority.toPacketEvents()) {
                    override fun onPacketReceive(event: PacketReceiveEvent) {
                        if (event.packetType != PacketType.Play.Client.PLAYER_DIGGING) return

                        val player = runCatching { event.getPlayer<Player>() }.getOrNull() ?: return
                        val dig = WrapperPlayClientPlayerDigging(event)
                        val block = dig.blockPosition
                        val sequence = dig.sequence

                        BlockDigContext(
                            player = player,
                            world = player.world,
                            x = block.x,
                            y = block.y,
                            z = block.z,
                            action = dig.action.toSculk(),
                            sequence = sequence,
                            scheduler = scheduler,
                            cancelAction = { event.isCancelled = true },
                            acknowledgeAction = { acknowledge(player, sequence) },
                        ).let { context -> guard.run("block dig") { context.handler() } }
                    }
                }

            return register(listener) { "PacketEvents failed to register a dig listener for ${plugin.name}." }
        }

        override fun listenUse(priority: PacketPriority, handler: BlockUseContext.() -> Unit): SculkResult<SculkHandle> {
            val listener =
                object : PacketListenerAbstract(priority.toPacketEvents()) {
                    override fun onPacketReceive(event: PacketReceiveEvent) {
                        if (event.packetType != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return

                        val player = runCatching { event.getPlayer<Player>() }.getOrNull() ?: return
                        val use = WrapperPlayClientPlayerBlockPlacement(event)
                        val block = use.blockPosition
                        val sequence = use.sequence

                        BlockUseContext(
                            player = player,
                            world = player.world,
                            x = block.x,
                            y = block.y,
                            z = block.z,
                            face = runCatching { use.face.name }.getOrNull(),
                            offHand = runCatching { use.hand == InteractionHand.OFF_HAND }.getOrDefault(false),
                            sequence = sequence,
                            scheduler = scheduler,
                            cancelAction = { event.isCancelled = true },
                            acknowledgeAction = { acknowledge(player, sequence) },
                        ).let { context -> guard.run("block use") { context.handler() } }
                    }
                }

            return register(listener) { "PacketEvents failed to register a block-use listener for ${plugin.name}." }
        }
    }

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
                        val context = event.toContext(direction, type)
                        guard.run("$type") { context.handler() }
                    }
                }

                override fun onPacketSend(event: PacketSendEvent) {
                    if (direction == PacketDirection.Clientbound && event.packetType == packetType) {
                        val context = event.toContext(direction, type)
                        guard.run("$type") { context.handler() }
                    }
                }
            }

        return register(listener) { "PacketEvents failed to register $type for ${plugin.name}." }
    }

    private fun register(listener: PacketListenerAbstract, failure: () -> String): SculkResult<SculkHandle> = runCatching {
        PacketEvents.getAPI().eventManager.registerListener(listener)
    }.fold(
        onSuccess = { registered ->
            val handle = SculkHandle { unregister(registered) }
            handles += handle
            SculkResult.success(handle)
        },
        onFailure = { SculkResult.failure(failure(), it) },
    )

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
        packet = PacketEventsEvent(this, direction, type),
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

    private fun DiggingAction.toSculk(): BlockDigAction = when (this) {
        DiggingAction.START_DIGGING -> BlockDigAction.Start
        DiggingAction.CANCELLED_DIGGING -> BlockDigAction.Abort
        DiggingAction.FINISHED_DIGGING -> BlockDigAction.Finish
        else -> BlockDigAction.Other
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

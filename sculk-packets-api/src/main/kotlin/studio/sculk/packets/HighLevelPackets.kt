package studio.sculk.packets

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.scheduler.SculkScheduler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Shows one player blocks that are not really there, without ever touching the world.
 *
 * ```kotlin
 * sculk.packets.clientBlocks.onDig {
 *     if (action != BlockDigAction.Start || !mine.owns(x, y, z)) return@onDig
 *     intercept { mine.award(player) }
 * }
 * ```
 */
@SculkStable
public class ClientBlockService internal constructor(private val scheduler: SculkScheduler, private val backend: ClientBlockBackend?) {
    public fun set(player: Player, location: Location, material: Material): SculkResult<Unit> =
        set(player, location, material.createBlockData())

    /**
     * Sends [data] to [player] at [location]. Prefer this over the [Material] overload in hot
     * paths: block data can be built once and reused, where a material is parsed on every send.
     */
    public fun set(player: Player, location: Location, data: BlockData): SculkResult<Unit> {
        scheduler.runNow(player) {
            player.sendBlockChange(location, data)
        }
        return SculkResult.success(Unit)
    }

    public fun reset(player: Player, location: Location): SculkResult<Unit> {
        scheduler.runNow(location) {
            player.sendBlockChange(location, location.block.blockData)
        }
        return SculkResult.success(Unit)
    }

    public fun preview(player: Player, location: Location, material: Material, durationTicks: Long): SculkResult<SculkHandle> =
        preview(player, location, material.createBlockData(), durationTicks)

    public fun preview(player: Player, location: Location, data: BlockData, durationTicks: Long): SculkResult<SculkHandle> {
        set(player, location, data)
        val handle =
            scheduler.runSyncDelayed(player, durationTicks) {
                reset(player, location)
            }
        return SculkResult.success(handle)
    }

    /**
     * Closes the client's block prediction for [sequence], letting it render server block
     * updates at that position again.
     *
     * Only needed when something cancelled the dig or use-item packet that opened the
     * prediction, since that also suppresses vanilla's acknowledgement. Dig handlers should
     * prefer [BlockDigContext.intercept], which orders this correctly on its own.
     */
    public fun acknowledge(player: Player, sequence: Int): SculkResult<Unit> =
        backend?.acknowledge(player, sequence) ?: SculkResult.failure(UNSUPPORTED)

    /**
     * Listens to every block a client reports digging. The handler runs on the packet thread;
     * see [BlockDigContext] for the cancel/acknowledge contract.
     */
    public fun onDig(priority: PacketPriority, handler: BlockDigContext.() -> Unit): SculkResult<SculkHandle> =
        backend?.listenDig(priority, handler) ?: SculkResult.failure(UNSUPPORTED)

    public fun onDig(handler: BlockDigContext.() -> Unit): SculkResult<SculkHandle> = onDig(PacketPriority.Normal, handler)

    /**
     * Listens to every block a client right-clicks. The handler runs on the packet thread.
     *
     * A virtual block needs this as much as it needs [onDig]: interacting opens a prediction at
     * that position too, so an unhandled right click snaps the block back to the real world.
     */
    public fun onUse(priority: PacketPriority, handler: BlockUseContext.() -> Unit): SculkResult<SculkHandle> =
        backend?.listenUse(priority, handler) ?: SculkResult.failure(UNSUPPORTED)

    public fun onUse(handler: BlockUseContext.() -> Unit): SculkResult<SculkHandle> = onUse(PacketPriority.Normal, handler)

    /** Java-friendly overload of [onUse] taking a [java.util.function.Consumer]. */
    @SculkStable
    public fun onUse(priority: PacketPriority, handler: java.util.function.Consumer<BlockUseContext>): SculkResult<SculkHandle> =
        onUse(priority) { handler.accept(this) }

    /** Java-friendly convenience overload of [onUse] at [Normal][PacketPriority.Normal] priority. */
    @SculkStable
    public fun onUse(handler: java.util.function.Consumer<BlockUseContext>): SculkResult<SculkHandle> =
        onUse(PacketPriority.Normal) { handler.accept(this) }

    /** Java-friendly overload of [onDig] taking a [java.util.function.Consumer]. */
    @SculkStable
    public fun onDig(priority: PacketPriority, handler: java.util.function.Consumer<BlockDigContext>): SculkResult<SculkHandle> =
        onDig(priority) { handler.accept(this) }

    /** Java-friendly convenience overload of [onDig] at [Normal][PacketPriority.Normal] priority. */
    @SculkStable
    public fun onDig(handler: java.util.function.Consumer<BlockDigContext>): SculkResult<SculkHandle> =
        onDig(PacketPriority.Normal) { handler.accept(this) }

    private companion object {
        const val UNSUPPORTED = "The active packet backend does not support client block digging."
    }
}

@SculkStable
public class PacketDebugService internal constructor(private val service: SculkPacketService, private val scheduler: SculkScheduler) {
    /** Java-friendly overload of [session] taking a [java.util.function.Consumer]. */
    @SculkStable
    public fun session(block: java.util.function.Consumer<PacketDebugBuilder>): SculkResult<SculkHandle> = session { block.accept(this) }

    public fun session(block: PacketDebugBuilder.() -> Unit): SculkResult<SculkHandle> {
        val request = PacketDebugBuilder().apply(block)
        val handles = mutableListOf<SculkHandle>()

        request.incoming.forEach { key ->
            val result =
                service.listen(PacketDirection.Serverbound, key, PacketPriority.Monitor) {
                    if (request.player == null || request.player == player) request.onPacket(this)
                }
            when (result) {
                is SculkResult.Success -> handles += result.value
                is SculkResult.Failure -> return result
            }
        }

        request.outgoing.forEach { key ->
            val result =
                service.listen(PacketDirection.Clientbound, key, PacketPriority.Monitor) {
                    if (request.player == null || request.player == player) request.onPacket(this)
                }
            when (result) {
                is SculkResult.Success -> handles += result.value
                is SculkResult.Failure -> return result
            }
        }

        val group =
            SculkHandle {
                handles.asReversed().forEach { it.close() }
            }
        val durationTicks = (request.duration.inWholeMilliseconds / 50).coerceAtLeast(1)
        handles +=
            scheduler.runSyncDelayed(durationTicks) {
                group.close()
            }
        return SculkResult.success(group)
    }
}

@SculkStable
public class PacketDebugBuilder {
    internal val incoming: MutableList<PacketKey> = mutableListOf()
    internal val outgoing: MutableList<PacketKey> = mutableListOf()
    internal var onPacket: PacketContext.() -> Unit = {}

    public var player: Player? = null
    public var duration: Duration = 10.seconds

    public fun player(value: Player) {
        player = value
    }

    public fun incoming(value: String) {
        incoming += PacketKey.of(value)
    }

    public fun outgoing(value: String) {
        outgoing += PacketKey.of(value)
    }

    public fun onPacket(block: PacketContext.() -> Unit) {
        onPacket = block
    }

    /** Java-friendly overload of [onPacket] taking a [java.util.function.Consumer]. */
    @SculkStable
    public fun onPacket(block: java.util.function.Consumer<PacketContext>) {
        onPacket = { block.accept(this) }
    }
}

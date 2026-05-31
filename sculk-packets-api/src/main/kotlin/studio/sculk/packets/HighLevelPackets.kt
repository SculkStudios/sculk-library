package studio.sculk.packets

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.scheduler.SculkScheduler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public class ClientBlockService internal constructor(
    private val scheduler: SculkScheduler,
) {
    public fun set(
        player: Player,
        location: Location,
        material: Material,
    ): SculkResult<Unit> {
        scheduler.runSync(player) {
            player.sendBlockChange(location, material.createBlockData())
        }
        return SculkResult.success(Unit)
    }

    public fun reset(
        player: Player,
        location: Location,
    ): SculkResult<Unit> {
        scheduler.runSync(location) {
            player.sendBlockChange(location, location.block.blockData)
        }
        return SculkResult.success(Unit)
    }

    public fun preview(
        player: Player,
        location: Location,
        material: Material,
        durationTicks: Long,
    ): SculkResult<SculkHandle> {
        set(player, location, material)
        val handle =
            scheduler.runSyncDelayed(player, durationTicks) {
                reset(player, location)
            }
        return SculkResult.success(handle)
    }
}

public class PacketDebugService internal constructor(
    private val service: SculkPacketService,
    private val scheduler: SculkScheduler,
) {
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
}

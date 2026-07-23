package studio.sculk.content

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.packets.BlockDigContext
import studio.sculk.packets.BlockUseContext
import studio.sculk.packets.PacketPriority
import studio.sculk.packets.SculkPacketService

/**
 * High-level client-side content helpers backed by a [SculkPacketService].
 */
@SculkStable
public class SculkContent public constructor(private val packets: SculkPacketService) {
    public val clientBlocks: ClientBlockContent = ClientBlockContent(packets)
}

/** Creates a high-level content facade for this packet service. */
@SculkStable
public val SculkPacketService.content: SculkContent
    get() = SculkContent(this)

@SculkStable
public class ClientBlockContent internal constructor(private val packets: SculkPacketService) {
    public fun set(player: Player, location: Location, material: Material): SculkResult<Unit> =
        packets.clientBlocks.set(player, location, material)

    public fun set(player: Player, location: Location, data: BlockData): SculkResult<Unit> =
        packets.clientBlocks.set(player, location, data)

    public fun reset(player: Player, location: Location): SculkResult<Unit> = packets.clientBlocks.reset(player, location)

    public fun preview(player: Player, location: Location, material: Material, durationTicks: Long): SculkResult<SculkHandle> =
        packets.clientBlocks.preview(player, location, material, durationTicks)

    public fun preview(player: Player, location: Location, data: BlockData, durationTicks: Long): SculkResult<SculkHandle> =
        packets.clientBlocks.preview(player, location, data, durationTicks)

    /** See [studio.sculk.packets.ClientBlockService.acknowledge]. */
    public fun acknowledge(player: Player, sequence: Int): SculkResult<Unit> = packets.clientBlocks.acknowledge(player, sequence)

    /** See [studio.sculk.packets.ClientBlockService.onDig]. */
    public fun onDig(priority: PacketPriority, handler: BlockDigContext.() -> Unit): SculkResult<SculkHandle> =
        packets.clientBlocks.onDig(priority, handler)

    public fun onDig(handler: BlockDigContext.() -> Unit): SculkResult<SculkHandle> = packets.clientBlocks.onDig(handler)

    /** See [studio.sculk.packets.ClientBlockService.onUse]. */
    public fun onUse(priority: PacketPriority, handler: BlockUseContext.() -> Unit): SculkResult<SculkHandle> =
        packets.clientBlocks.onUse(priority, handler)

    public fun onUse(handler: BlockUseContext.() -> Unit): SculkResult<SculkHandle> = packets.clientBlocks.onUse(handler)

    /** Java-friendly overload of [onUse] taking a [java.util.function.Consumer]. */
    @SculkStable
    public fun onUse(priority: PacketPriority, handler: java.util.function.Consumer<BlockUseContext>): SculkResult<SculkHandle> =
        packets.clientBlocks.onUse(priority, handler)

    /** Java-friendly convenience overload of [onUse] at [Normal][PacketPriority.Normal] priority. */
    @SculkStable
    public fun onUse(handler: java.util.function.Consumer<BlockUseContext>): SculkResult<SculkHandle> = packets.clientBlocks.onUse(handler)

    /** Java-friendly overload of [onDig] taking a [java.util.function.Consumer]. */
    @SculkStable
    public fun onDig(priority: PacketPriority, handler: java.util.function.Consumer<BlockDigContext>): SculkResult<SculkHandle> =
        packets.clientBlocks.onDig(priority, handler)

    /** Java-friendly convenience overload of [onDig] at [Normal][PacketPriority.Normal] priority. */
    @SculkStable
    public fun onDig(handler: java.util.function.Consumer<BlockDigContext>): SculkResult<SculkHandle> = packets.clientBlocks.onDig(handler)
}

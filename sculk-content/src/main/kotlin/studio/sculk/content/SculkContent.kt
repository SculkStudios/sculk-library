package studio.sculk.content

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import studio.sculk.core.SculkHandle
import studio.sculk.core.SculkResult
import studio.sculk.packets.SculkPacketService

/**
 * High-level client-side content helpers backed by a [SculkPacketService].
 */
public class SculkContent public constructor(
    private val packets: SculkPacketService,
) {
    public val clientBlocks: ClientBlockContent = ClientBlockContent(packets)
}

/** Creates a high-level content facade for this packet service. */
public val SculkPacketService.content: SculkContent
    get() = SculkContent(this)

public class ClientBlockContent internal constructor(
    private val packets: SculkPacketService,
) {
    public fun set(
        player: Player,
        location: Location,
        material: Material,
    ): SculkResult<Unit> = packets.clientBlocks.set(player, location, material)

    public fun reset(
        player: Player,
        location: Location,
    ): SculkResult<Unit> = packets.clientBlocks.reset(player, location)

    public fun preview(
        player: Player,
        location: Location,
        material: Material,
        durationTicks: Long,
    ): SculkResult<SculkHandle> = packets.clientBlocks.preview(player, location, material, durationTicks)
}

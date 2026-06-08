package studio.sculk.packets

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable

/**
 * Backend-neutral packet service.
 *
 * Prefer the high-level services for common features. Low-level listeners are for advanced packet
 * work and must follow packet thread-safety rules.
 */
@SculkStable
public interface SculkPacketService : SculkHandle {
    public val backend: PacketBackend
    public val clientBlocks: ClientBlockService
    public val debug: PacketDebugService

    public fun listen(
        direction: PacketDirection,
        type: PacketKey,
        priority: PacketPriority = PacketPriority.Normal,
        handler: PacketContext.() -> Unit,
    ): SculkResult<SculkHandle>

    /** Java-friendly overload of [listen] taking a [java.util.function.Consumer]. */
    @SculkStable
    public fun listen(
        direction: PacketDirection,
        type: PacketKey,
        priority: PacketPriority,
        handler: java.util.function.Consumer<PacketContext>,
    ): SculkResult<SculkHandle> = listen(direction, type, priority) { handler.accept(this) }

    /** Java-friendly convenience overload of [listen] at [Normal][PacketPriority.Normal] priority. */
    @SculkStable
    public fun listen(
        direction: PacketDirection,
        type: PacketKey,
        handler: java.util.function.Consumer<PacketContext>,
    ): SculkResult<SculkHandle> = listen(direction, type, PacketPriority.Normal) { handler.accept(this) }

    public fun send(player: Player, packet: SculkPacket): SculkResult<Unit>
}

@SculkStable
public interface SculkPacketServiceProvider {
    public val backend: PacketBackend

    public fun isAvailable(): Boolean

    public fun create(plugin: JavaPlugin, scheduler: studio.sculk.scheduler.SculkScheduler): SculkPacketService
}

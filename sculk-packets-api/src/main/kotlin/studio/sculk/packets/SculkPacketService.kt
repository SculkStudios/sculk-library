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

    /**
     * Display entities that exist only in a client's view.
     *
     * Backends that cannot do this return an unavailable service whose calls fail by name, rather
     * than the property being absent and the caller having to know which backend they are on.
     */
    public val virtualEntities: VirtualEntityService
    public val debug: PacketDebugService

    public fun listen(
        direction: PacketDirection,
        type: PacketKey,
        priority: PacketPriority = PacketPriority.Low,
        handler: PacketContext.() -> Unit,
    ): SculkResult<SculkHandle>

    public fun send(player: Player, packet: SculkPacket): SculkResult<Unit>
}

@SculkStable
public interface SculkPacketServiceProvider {
    public val backend: PacketBackend

    public fun isAvailable(): Boolean

    public fun create(plugin: JavaPlugin, scheduler: studio.sculk.scheduler.SculkScheduler): SculkPacketService
}

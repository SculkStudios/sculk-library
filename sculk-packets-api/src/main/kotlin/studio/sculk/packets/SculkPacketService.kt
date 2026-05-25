package studio.sculk.packets

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.core.SculkHandle
import studio.sculk.core.SculkResult

/**
 * Backend-neutral packet service.
 *
 * Prefer the high-level services for common features. Low-level listeners are for advanced packet
 * work and must follow packet thread-safety rules.
 */
public interface SculkPacketService : SculkHandle {
    public val backend: PacketBackend
    public val holograms: PacketHologramService
    public val fakeEntities: FakeEntityService
    public val clientBlocks: ClientBlockService
    public val nametags: NametagService
    public val debug: PacketDebugService

    public fun listen(
        direction: PacketDirection,
        type: PacketKey,
        priority: PacketPriority = PacketPriority.Normal,
        handler: PacketContext.() -> Unit,
    ): SculkResult<SculkHandle>

    public fun send(
        player: Player,
        packet: SculkPacket,
    ): SculkResult<Unit>
}

public interface SculkPacketServiceProvider {
    public val backend: PacketBackend

    public fun isAvailable(): Boolean

    public fun create(
        plugin: JavaPlugin,
        scheduler: studio.sculk.core.scheduler.SculkScheduler,
    ): SculkPacketService
}

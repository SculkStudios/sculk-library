package studio.sculk.packets

import org.bukkit.entity.Player
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import studio.sculk.scheduler.SculkScheduler

/**
 * Context passed to low-level packet listeners.
 *
 * Packet callbacks may run away from the main/region thread. Use [runSync] before touching
 * Bukkit/Paper APIs that require a synchronized context.
 */
@SculkStable
public class PacketContext @JvmOverloads constructor(
    public val player: Player?,
    public val direction: PacketDirection,
    public val type: PacketKey,
    private val scheduler: SculkScheduler,
    private val cancelAction: () -> Unit,
    private val markChangedAction: () -> Unit,
    /**
     * The backend's own packet, when the adapter can expose one.
     *
     * Reading or writing it means depending on that backend, so prefer the high-level services
     * where they cover the need. Use [packetAs] to narrow it safely.
     */
    public val packet: SculkPacket? = null,
) {
    public var cancelled: Boolean = false
        private set

    public var changed: Boolean = false
        private set

    public fun cancel() {
        cancelled = true
        cancelAction()
    }

    /**
     * Marks the packet as changed.
     *
     * PacketEvents requires packet wrappers to be re-encoded after modification. ProtocolLib can
     * treat this as a no-op when changes are applied directly to its packet container.
     */
    public fun markChanged() {
        changed = true
        markChangedAction()
    }

    /** The backend [packet] narrowed to [type], or null if absent or of another type. */
    public fun <T : SculkPacket> packetAs(type: Class<T>): T? = if (type.isInstance(packet)) type.cast(packet) else null

    /**
     * Runs [block] on the safe sync context for the current player when available.
     */
    public fun runSync(block: () -> Unit): SculkHandle = if (player != null) {
        scheduler.runSync(player, Runnable(block))
    } else {
        scheduler.runSync(Runnable(block))
    }
}

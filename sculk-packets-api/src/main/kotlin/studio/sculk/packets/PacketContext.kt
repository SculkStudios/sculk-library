package studio.sculk.packets

import org.bukkit.entity.Player
import studio.sculk.SculkHandle
import studio.sculk.scheduler.SculkScheduler

/**
 * Context passed to low-level packet listeners.
 *
 * Packet callbacks may run away from the main/region thread. Use [runSync] before touching
 * Bukkit/Paper APIs that require a synchronized context.
 */
public class PacketContext(
    public val player: Player?,
    public val direction: PacketDirection,
    public val type: PacketKey,
    private val scheduler: SculkScheduler,
    private val cancelAction: () -> Unit,
    private val markChangedAction: () -> Unit,
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

    /**
     * Runs [block] on the safe sync context for the current player when available.
     */
    public fun runSync(block: () -> Unit): SculkHandle =
        if (player != null) {
            scheduler.runSync(player, Runnable(block))
        } else {
            scheduler.runSync(Runnable(block))
        }
}

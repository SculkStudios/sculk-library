package studio.sculk.holograms

import org.bukkit.Location
import studio.sculk.annotation.SculkStable

/**
 * A handle to a virtual (packet-only) hologram managed by a [HologramService].
 *
 * Mutating methods are expected to be called from the main/region thread.
 */
@SculkStable
public interface Hologram {
    /** Replaces the displayed lines (each parsed as MiniMessage); re-sent to viewers on the next tick. */
    public fun setLines(lines: List<String>)

    /** Moves the hologram; current viewers are refreshed on the next reconcile tick. */
    public fun teleport(location: Location)

    /** Despawns the hologram for all viewers and unregisters it. */
    public fun remove()
}

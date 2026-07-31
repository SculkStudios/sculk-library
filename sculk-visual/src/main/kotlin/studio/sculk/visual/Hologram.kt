package studio.sculk.visual

import org.bukkit.Location
import studio.sculk.annotation.SculkStable
import studio.sculk.packets.Billboard

/** How a hologram looks and how far it carries. */
@SculkStable
public data class HologramOptions(
    /** Players within this many blocks, in the same world, see it. */
    public val viewRangeBlocks: Double = 48.0,
    public val billboard: Billboard = Billboard.CENTER,
    /**
     * ARGB background. Zero is fully transparent — the dark plate behind the text is drawn unless
     * the colour's alpha is zero, and turning the "default background" flag off alone is not
     * enough.
     */
    public val backgroundArgb: Int = 0,
    public val lineWidthPixels: Int = 200,
    public val shadowed: Boolean = false,
    public val seeThrough: Boolean = false,
    public val scale: Float = 1.0f,
    /** Added to the spawn location, so a hologram can sit above the block it describes. */
    public val yOffset: Double = 0.0,
)

/** A hologram that exists only in the clients that can see it. */
@SculkStable
public interface Hologram {
    /** Replaces the lines. Re-sent to viewers on the next reconcile, not immediately. */
    public fun setLines(lines: List<String>)

    /** Moves it. Viewers are recomputed on the next reconcile. */
    public fun teleport(location: Location)

    /** Despawns it for everyone and stops tracking it. */
    public fun remove()
}

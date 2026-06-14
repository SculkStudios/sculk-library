package studio.sculk.holograms

import studio.sculk.annotation.SculkStable

/** How a [Hologram] orients itself toward the viewer (vanilla TextDisplay billboard modes). */
@SculkStable
public enum class Billboard(public val id: Byte) {
    FIXED(0),
    VERTICAL(1),
    HORIZONTAL(2),
    CENTER(3),
}

/** Tunables for a [Hologram]. */
@SculkStable
public data class HologramOptions(
    /** Players within this many blocks (same world, loaded chunk) see the hologram. */
    public val viewRangeBlocks: Double = 48.0,
    /** Billboard rotation mode. [Billboard.CENTER] always faces the player. */
    public val billboard: Billboard = Billboard.CENTER,
    /** ARGB background color. `0` is fully transparent — no dark text box. */
    public val backgroundArgb: Int = 0,
    /** Maximum text line width in pixels before wrapping. */
    public val lineWidthPixels: Int = 200,
    /** Vertical offset (blocks) added to the spawn location. */
    public val yOffset: Double = 0.0,
)

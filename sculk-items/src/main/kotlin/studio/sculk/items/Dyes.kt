package studio.sculk.items

import org.bukkit.Material
import studio.sculk.annotation.SculkStable

/**
 * Picks the dye that comes closest to an arbitrary colour.
 *
 * For menus that need an icon in a colour a theme chose. There are sixteen dyes and no way to tint
 * an item, so a colour picker either matches by eye in a lookup table that goes stale the moment
 * the palette changes, or it measures. This measures.
 */
@SculkStable
public object Dyes {
    private val dyes: List<Pair<Material, Int>> = listOf(
        Material.WHITE_DYE to 0xF9FFFE,
        Material.ORANGE_DYE to 0xF9801D,
        Material.MAGENTA_DYE to 0xC74EBD,
        Material.LIGHT_BLUE_DYE to 0x3AB3DA,
        Material.YELLOW_DYE to 0xFED83D,
        Material.LIME_DYE to 0x80C71F,
        Material.PINK_DYE to 0xF38BAA,
        Material.GRAY_DYE to 0x474F52,
        Material.LIGHT_GRAY_DYE to 0x9D9D97,
        Material.CYAN_DYE to 0x169C9C,
        Material.PURPLE_DYE to 0x8932B8,
        Material.BLUE_DYE to 0x3C44AA,
        Material.BROWN_DYE to 0x835432,
        Material.GREEN_DYE to 0x5E7C16,
        Material.RED_DYE to 0xB02E26,
        Material.BLACK_DYE to 0x1D1D21,
    )

    /** The dye nearest [rgb], compared as squared distance in RGB space. */
    @SculkStable
    public fun nearest(rgb: Int): Material {
        val red = (rgb shr 16) and 0xFF
        val green = (rgb shr 8) and 0xFF
        val blue = rgb and 0xFF
        return dyes.minBy { (_, colour) ->
            val dr = red - ((colour shr 16) and 0xFF)
            val dg = green - ((colour shr 8) and 0xFF)
            val db = blue - (colour and 0xFF)
            dr * dr + dg * dg + db * db
        }.first
    }

    /** The dye nearest a `#rrggbb` string, or white when it cannot be read. */
    @SculkStable
    public fun nearest(hex: String): Material = hex.removePrefix("#").toIntOrNull(16)?.let { nearest(it) } ?: Material.WHITE_DYE
}

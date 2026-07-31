package studio.sculk.text

import studio.sculk.annotation.SculkStable

private val HEX = Regex("^#[0-9a-fA-F]{6}$")

/**
 * How one named theme style turns into MiniMessage.
 *
 * A style is expanded into an opening and closing tag pair *before* MiniMessage parses, which is
 * the only way a gradient can be scoped: `<gradient>` colours everything up to its close tag, so
 * it cannot be expressed as a single tag substitution the way a colour can.
 */
@SculkStable
public sealed interface ThemeStyle {
    /** The MiniMessage this style opens with. */
    public val open: String

    /** The MiniMessage that closes it. */
    public val close: String

    /**
     * One representative colour, for places that cannot render the full style — a dyed menu icon,
     * a boss bar colour, a log line.
     */
    public val swatchHex: String

    /** A single colour. */
    @SculkStable
    public data class Solid(public val hex: String) : ThemeStyle {
        init {
            require(HEX.matches(hex)) { "Theme colour must be #rrggbb, got '$hex'." }
        }

        override val open: String get() = "<color:$hex>"
        override val close: String get() = "</color>"
        override val swatchHex: String get() = hex
    }

    /** A gradient across two or more stops. */
    @SculkStable
    public data class Gradient(public val hexes: List<String>) : ThemeStyle {
        init {
            require(hexes.size >= 2) { "A gradient needs at least two colours, got ${hexes.size}." }
            hexes.forEach { require(HEX.matches(it)) { "Theme colour must be #rrggbb, got '$it'." } }
        }

        override val open: String get() = "<gradient:${hexes.joinToString(":")}>"
        override val close: String get() = "</gradient>"

        /** The middle stop, which reads closer to the whole gradient than either end does. */
        override val swatchHex: String get() = hexes[hexes.size / 2]
    }

    /**
     * A flat colour that a resource pack's core shader recognises by exact value and animates.
     *
     * The colour is a sentinel, not a colour anyone chose to look at: the pack matches the RGB
     * triple and substitutes its own per-frame output. Without the pack the client renders the
     * sentinel as-is, so the text degrades to a flat colour instead of breaking.
     *
     * Keep a sentinel one step away in one channel from any static colour it sits beside, or the
     * shader animates text that was meant to stay still.
     */
    @SculkStable
    public data class Shader(public val hex: String, public val effect: String) : ThemeStyle {
        init {
            require(HEX.matches(hex)) { "Theme colour must be #rrggbb, got '$hex'." }
            require(effect.isNotBlank()) { "A shader style must name its effect." }
        }

        override val open: String get() = "<color:$hex>"
        override val close: String get() = "</color>"
        override val swatchHex: String get() = hex
    }
}

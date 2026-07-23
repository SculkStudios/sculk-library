package studio.sculk.adventure

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextDecoration
import studio.sculk.annotation.SculkStable

/**
 * Server-wide defaults applied to every string Sculk parses.
 *
 * Two things every polished server ends up wanting, and which are tedious to
 * repeat on every message:
 *
 *  - **A drop shadow.** Modern clients render a per-component shadow colour;
 *    a soft dark one under bright pastel text is the difference between chat
 *    that looks designed and chat that looks default.
 *  - **No accidental italics.** Item lore and names are italic unless told
 *    otherwise, which silently ruins otherwise careful formatting.
 *
 * Both are applied *if absent*, so a message that sets its own shadow or turns
 * italics on explicitly always wins. Configure once at startup:
 *
 * ```kotlin
 * SculkTextStyle.shadow(0x99000000.toInt())
 * ```
 *
 * Defaults are off, so existing plugins render exactly as before until they opt in.
 */
@SculkStable
public object SculkTextStyle {
    @Volatile
    private var shadow: ShadowColor? = null

    @Volatile
    private var suppressItalics: Boolean = false

    /** The shadow applied to parsed text, or null when none is set. */
    @SculkStable
    public fun shadow(): ShadowColor? = shadow

    /** Sets the default shadow from a packed ARGB int, e.g. `0x99000000.toInt()`. */
    @SculkStable
    public fun shadow(argb: Int) {
        shadow = ShadowColor.shadowColor(argb)
    }

    /**
     * Sets the default shadow from a `#RRGGBB` or `#AARRGGBB` string.
     *
     * A six-digit value is taken as fully opaque. Returns false for anything
     * unparseable rather than throwing, so a bad config value cannot stop startup.
     */
    @SculkStable
    public fun shadow(hex: String): Boolean {
        val cleaned = hex.removePrefix("#")
        val argb =
            when (cleaned.length) {
                6 -> cleaned.toLongOrNull(16)?.let { it or 0xFF000000L }
                8 -> cleaned.toLongOrNull(16)
                else -> null
            } ?: return false

        shadow = ShadowColor.shadowColor(argb.toInt())
        return true
    }

    /** Removes the default shadow. */
    @SculkStable
    public fun clearShadow() {
        shadow = null
    }

    /** When true, parsed text is non-italic unless it asks for italics itself. */
    @SculkStable
    public fun suppressItalics(value: Boolean) {
        suppressItalics = value
    }

    /** Applies the current defaults to [component]. Returns it unchanged when nothing is configured. */
    @SculkStable
    public fun apply(component: Component): Component {
        var styled = component
        shadow?.let { styled = styled.shadowColorIfAbsent(it) }
        if (suppressItalics) {
            styled = styled.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)
        }
        return styled
    }
}

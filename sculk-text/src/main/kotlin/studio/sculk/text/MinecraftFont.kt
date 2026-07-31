package studio.sculk.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.TextDecoration
import studio.sculk.annotation.SculkStable

/**
 * Measures text the way the vanilla client draws it.
 *
 * Minecraft's default font is proportional — `i` is two pixels wide and `@` is seven — so
 * centring by character count puts text visibly off-centre wherever it matters most: sidebars,
 * holograms, GUI titles, anything with a fixed frame around it.
 *
 * ### Where this is approximate
 *
 * Widths are for the default font and ASCII. Unicode outside that range is assumed to be six
 * pixels, which is right for most Latin glyphs and wrong for CJK (those are eight). A resource
 * pack that replaces the font invalidates all of it.
 */
@SculkStable
public object MinecraftFont {
    /** Width of a space, including the pixel of spacing after it. */
    @SculkStable
    public const val SPACE_WIDTH: Int = 4

    private const val DEFAULT_WIDTH = 6

    // Widths include the one pixel of spacing the client draws after every glyph. Anything not
    // listed here is six pixels wide, which covers most of the alphabet and the digits.
    private val widths: Map<Char, Int> = buildMap {
        putAll("!.,:;|i".associateWith { 2 })
        putAll("'`l".associateWith { 3 })
        putAll(" []It".associateWith { 4 })
        putAll("\"()*<>{}fk".associateWith { 5 })
        putAll("@~".associateWith { 7 })
    }

    /** The width of [text] in pixels. Bold adds one pixel per character. */
    @SculkStable
    public fun width(text: String, bold: Boolean = false): Int {
        var total = 0
        for (char in text) {
            total += (widths[char] ?: DEFAULT_WIDTH) + if (bold) 1 else 0
        }
        return total
    }

    /**
     * The width of [component] in pixels, honouring bold per part.
     *
     * Measuring the flattened string instead would under-measure any component with a bold word
     * in it, which is exactly the case centring is used for — a bold label beside a plain value.
     */
    @SculkStable
    public fun width(component: Component): Int = width(component, bold = false)

    private fun width(component: Component, bold: Boolean): Int {
        val boldHere = when (component.decoration(TextDecoration.BOLD)) {
            TextDecoration.State.TRUE -> true
            TextDecoration.State.FALSE -> false
            else -> bold
        }
        var total = if (component is TextComponent) width(component.content(), boldHere) else 0
        for (child in component.children()) {
            total += width(child, boldHere)
        }
        return total
    }

    /**
     * Spaces to prefix [text] with so it sits centred in [availableWidth] pixels.
     *
     * Returns the padding only, not the padded string, because callers usually need to know the
     * padding changed — a still row still has to be redrawn when the widest row around it moved.
     */
    @SculkStable
    public fun centre(text: String, availableWidth: Int, bold: Boolean = false): String = padding(width(text, bold), availableWidth)

    /** As [centre], for an already-built component. */
    @SculkStable
    public fun centre(component: Component, availableWidth: Int): String = padding(width(component), availableWidth)

    private fun padding(contentWidth: Int, availableWidth: Int): String {
        val slack = availableWidth - contentWidth
        if (slack <= 0) return ""
        return " ".repeat(slack / 2 / SPACE_WIDTH)
    }
}

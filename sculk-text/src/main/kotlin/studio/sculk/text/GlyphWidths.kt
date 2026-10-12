package studio.sculk.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration
import studio.sculk.annotation.SculkStable

/**
 * Pixel widths of glyphs in one font, for centring and wrapping. [DEFAULT] covers Minecraft's
 * default font; resource-pack fonts get their own tables from the Sculk resource-pack compiler.
 *
 * A width is the glyph's advance, spacing included. Bold adds one pixel per glyph. Characters the
 * table does not know measure [fallback] wide, which is right for most Latin text and an estimate
 * for everything else.
 */
@SculkStable
public class GlyphWidths(private val advances: Map<Char, Int>, public val fallback: Int = DEFAULT_ADVANCE) {
    public fun width(char: Char, bold: Boolean = false): Int = (advances[char] ?: fallback) + if (bold) 1 else 0

    public fun width(text: String, bold: Boolean = false): Int = text.sumOf { width(it, bold) }

    /** The width of [component] as the client draws it, following bold through the component tree. */
    public fun width(component: Component): Int = segments(component).sumOf { (text, bold) -> width(text, bold) }

    /**
     * [component] preceded by enough spaces to centre it in a line [lineWidth] pixels wide, the chat
     * width by default. A component wider than the line is returned unchanged.
     */
    public fun centre(component: Component, lineWidth: Int = CHAT_WIDTH): Component {
        val padding = (lineWidth - width(component)) / 2
        if (padding <= 0) return component
        val spaces = padding / width(' ')
        return Component.text(" ".repeat(spaces)).append(component)
    }

    /**
     * Breaks [text] into lines no wider than [maxWidth], at spaces where possible and inside a word
     * only when the word alone is too wide.
     */
    public fun wrap(text: String, maxWidth: Int, bold: Boolean = false): List<String> {
        require(maxWidth >= fallback) { "A line must fit at least one glyph; $maxWidth is narrower than $fallback." }
        val lines = mutableListOf<String>()
        for (paragraph in text.split('\n')) {
            val line = StringBuilder()
            var lineWidth = 0
            for (word in paragraph.split(' ')) {
                val wordWidth = width(word, bold)
                val spaceWidth = if (line.isEmpty()) 0 else width(' ', bold)
                when {
                    lineWidth + spaceWidth + wordWidth <= maxWidth -> {
                        if (line.isNotEmpty()) line.append(' ')
                        line.append(word)
                        lineWidth += spaceWidth + wordWidth
                    }

                    wordWidth <= maxWidth -> {
                        lines += line.toString()
                        line.clear().append(word)
                        lineWidth = wordWidth
                    }

                    else -> {
                        if (line.isNotEmpty()) lines += line.toString()
                        line.clear()
                        lineWidth = 0
                        for (char in word) {
                            val charWidth = width(char, bold)
                            if (lineWidth + charWidth > maxWidth) {
                                lines += line.toString()
                                line.clear()
                                lineWidth = 0
                            }
                            line.append(char)
                            lineWidth += charWidth
                        }
                    }
                }
            }
            lines += line.toString()
        }
        return lines
    }

    private fun segments(component: Component, inherited: Style = Style.empty()): List<Pair<String, Boolean>> {
        val style = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET)
        val bold = style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE
        val own = (component as? TextComponent)?.content()?.let { listOf(it to bold) }.orEmpty()
        return own + component.children().flatMap { segments(it, style) }
    }

    @SculkStable
    public companion object {
        /** The width of the chat box at default scale, in pixels. */
        public const val CHAT_WIDTH: Int = 320

        private const val DEFAULT_ADVANCE = 6

        /** Minecraft's default font. Unlisted printable ASCII is six pixels wide. */
        public val DEFAULT: GlyphWidths = GlyphWidths(
            buildMap {
                "!.,:;i|'".forEach { put(it, 2) }
                "`l".forEach { put(it, 3) }
                " I[]t\"".forEach { put(it, 4) }
                "fk(){}<>*".forEach { put(it, 5) }
                put('@', 7)
                put('~', 7)
            },
        )
    }
}

package studio.sculk.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration
import studio.sculk.annotation.SculkStable

/**
 * Pixel widths of glyphs in one font, for centring and wrapping, keyed by code point. [DEFAULT]
 * covers Minecraft's default font; resource-pack fonts get their own tables from the Sculk
 * resource-pack compiler.
 *
 * A width is the glyph's advance, spacing included. Bold adds one pixel per glyph. Code points the
 * table does not know measure [fallback] wide, which is right for most Latin text and an estimate
 * for everything else.
 */
@SculkStable
public class GlyphWidths(advances: Map<Int, Int>, public val fallback: Int = DEFAULT_ADVANCE) {
    private val advances = advances.toMap()

    public fun width(codePoint: Int, bold: Boolean = false): Int = (advances[codePoint] ?: fallback) + if (bold) 1 else 0

    public fun width(char: Char, bold: Boolean = false): Int = width(char.code, bold)

    public fun width(text: String, bold: Boolean = false): Int = text.codePoints().map { width(it, bold) }.sum()

    /** The width of [component] as the client draws it, following bold through the component tree. */
    public fun width(component: Component): Int = segments(component).sumOf { (text, bold) -> width(text, bold) }

    /**
     * [component] preceded by enough spaces to centre it in a line [lineWidth] pixels wide, the chat
     * width by default. A component wider than the line is returned unchanged.
     */
    public fun centre(component: Component, lineWidth: Int = CHAT_WIDTH): Component {
        val padding = (lineWidth - width(component)) / 2
        if (padding <= 0) return component
        return Component.text(" ".repeat(padding / width(' '))).append(component)
    }

    /**
     * Breaks [text] into lines no wider than [maxWidth], at spaces where possible and inside a word
     * only when the word alone is too wide. A glyph wider than [maxWidth] gets a line of its own.
     */
    public fun wrap(text: String, maxWidth: Int, bold: Boolean = false): List<String> {
        require(maxWidth > 0) { "maxWidth must be positive, but was $maxWidth." }
        return text.split('\n').flatMap { paragraph -> LineBreaker(maxWidth, bold).breakParagraph(paragraph) }
    }

    private inner class LineBreaker(private val maxWidth: Int, private val bold: Boolean) {
        private val lines = mutableListOf<String>()
        private val line = StringBuilder()
        private var lineWidth = 0

        fun breakParagraph(paragraph: String): List<String> {
            for (word in paragraph.split(' ')) {
                val wordWidth = width(word, bold)
                val spaceWidth = if (line.isEmpty()) 0 else width(' ', bold)
                when {
                    lineWidth + spaceWidth + wordWidth <= maxWidth -> {
                        if (line.isNotEmpty()) line.append(' ')
                        append(word, spaceWidth + wordWidth)
                    }

                    wordWidth <= maxWidth -> {
                        newLine()
                        append(word, wordWidth)
                    }

                    else -> {
                        if (line.isNotEmpty()) newLine()
                        word.codePoints().forEach { codePoint ->
                            val glyphWidth = width(codePoint, bold)
                            if (line.isNotEmpty() && lineWidth + glyphWidth > maxWidth) newLine()
                            append(String(Character.toChars(codePoint)), glyphWidth)
                        }
                    }
                }
            }
            newLine()
            return lines
        }

        private fun append(text: String, width: Int) {
            line.append(text)
            lineWidth += width
        }

        private fun newLine() {
            lines += line.toString()
            line.clear()
            lineWidth = 0
        }
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
                "!',.:;i|".forEach { put(it.code, 2) }
                "`l".forEach { put(it.code, 3) }
                " \"()*I[]t{}".forEach { put(it.code, 4) }
                "<>fk".forEach { put(it.code, 5) }
                "@~".forEach { put(it.code, 7) }
            },
        )
    }
}

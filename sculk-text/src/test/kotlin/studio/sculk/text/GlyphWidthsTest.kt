package studio.sculk.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GlyphWidthsTest {
    private val font = GlyphWidths.DEFAULT

    @Test
    fun `advances match those measured from the vanilla 1_21_4 default font`() {
        // Measured from ascii.png in the 1.21.4 client: last opaque column + 1, plus one pixel of spacing. Space is from space.json.
        val measured = mapOf(
            '!' to 2, 'i' to 2, '.' to 2, '|' to 2,
            'l' to 3, '`' to 3,
            ' ' to 4, '(' to 4, ')' to 4, '*' to 4, '{' to 4, '}' to 4, 'I' to 4, 't' to 4, '[' to 4, '"' to 4,
            '<' to 5, '>' to 5, 'f' to 5, 'k' to 5,
            'A' to 6, 'W' to 6, '#' to 6, '0' to 6, '?' to 6,
            '@' to 7, '~' to 7,
        )
        measured.forEach { (char, advance) -> assertEquals(advance, font.width(char), "width of '$char'") }
        assertEquals(6 + 2 + 3 + 3 + 6, font.width("Hillo"))
    }

    @Test
    fun `bold adds a pixel per glyph and is inherited by children`() {
        assertEquals(font.width("abc") + 3, font.width("abc", bold = true))
        val component = Component.text("ab").decorate(TextDecoration.BOLD).append(Component.text("cd"))
        assertEquals(font.width("abcd", bold = true), font.width(component))
    }

    @Test
    fun `a surrogate pair measures as one glyph`() {
        assertEquals(font.fallback, font.width("😀"))
    }

    @Test
    fun `centring pads with spaces and leaves a too-wide line alone`() {
        val centred = font.centre(Component.text("Hi"), lineWidth = 100)
        val padding = PlainTextComponentSerializer.plainText().serialize(centred).takeWhile { it == ' ' }.length
        assertEquals((100 - font.width("Hi")) / 2 / font.width(' '), padding)
        val wide = Component.text("x".repeat(100))
        assertEquals(wide, font.centre(wide, lineWidth = 50))
    }

    @Test
    fun `wrapping breaks at spaces and splits only words that cannot fit`() {
        assertEquals(listOf("the quick", "brown fox"), font.wrap("the quick brown fox", maxWidth = font.width("brown fox")))
        assertEquals(listOf("abc", "def", "gh"), font.wrap("abcdefgh", maxWidth = font.width("abc")))
        assertEquals(listOf("one", "two"), font.wrap("one\ntwo", 500))
    }

    @Test
    fun `a glyph wider than the line gets a line of its own, never an empty one`() {
        assertEquals(listOf("@", "@"), font.wrap("@@", maxWidth = 6))
        assertEquals(listOf("a", "@", "b"), font.wrap("a @ b", maxWidth = 6))
    }

    @Test
    fun `wrapping never splits a surrogate pair`() {
        val emoji = "😀"
        assertEquals(listOf(emoji + emoji, emoji), font.wrap(emoji.repeat(3), maxWidth = 2 * font.fallback))
    }
}

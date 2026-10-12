package studio.sculk.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GlyphWidthsTest {
    private val font = GlyphWidths.DEFAULT

    @Test
    fun `narrow and wide glyphs measure as the default font draws them`() {
        assertEquals(2, font.width('i'))
        assertEquals(3, font.width('l'))
        assertEquals(4, font.width(' '))
        assertEquals(6, font.width('A'))
        assertEquals(7, font.width('@'))
        assertEquals(6 + 2 + 3 + 3 + 6, font.width("Hillo"))
    }

    @Test
    fun `bold adds a pixel per glyph and is inherited by children`() {
        assertEquals(font.width("abc") + 3, font.width("abc", bold = true))
        val component = Component.text("ab").decorate(TextDecoration.BOLD).append(Component.text("cd"))
        assertEquals(font.width("abcd", bold = true), font.width(component))
    }

    @Test
    fun `centring pads with spaces and leaves a too-wide line alone`() {
        val centred = font.centre(Component.text("Hi"), lineWidth = 100)
        val padding = PlainTextComponentSerializer.plainText().serialize(centred).takeWhile { it == ' ' }.length
        assertEquals((100 - font.width("Hi")) / 2 / 4, padding)
        val wide = Component.text("x".repeat(100))
        assertEquals(wide, font.centre(wide, lineWidth = 50))
    }

    @Test
    fun `wrapping breaks at spaces and splits only words that cannot fit`() {
        val lines = font.wrap("the quick brown fox", maxWidth = font.width("brown fox"))
        assertEquals(listOf("the quick", "brown fox"), lines)
        val split = font.wrap("abcdefgh", maxWidth = font.width("abc"))
        assertEquals(listOf("abc", "def", "gh"), split)
        assertTrue(font.wrap("one\ntwo", 500) == listOf("one", "two"))
    }
}

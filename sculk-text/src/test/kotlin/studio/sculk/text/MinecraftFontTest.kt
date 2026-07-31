package studio.sculk.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MinecraftFontTest {
    @Test
    fun `narrow glyphs measure narrower than wide ones`() {
        assertTrue(MinecraftFont.width("i") < MinecraftFont.width("m"))
        assertEquals(2, MinecraftFont.width("i"))
        assertEquals(7, MinecraftFont.width("@"))
    }

    @Test
    fun `bold adds one pixel per character`() {
        val plain = MinecraftFont.width("abc")

        assertEquals(plain + 3, MinecraftFont.width("abc", bold = true))
    }

    @Test
    fun `a component measured as a whole honours bold on only one part`() {
        val component = Component.text("ab")
            .append(Component.text("cd").decoration(TextDecoration.BOLD, true))

        // Measuring the flattened string would miss the two bold pixels entirely.
        assertEquals(MinecraftFont.width("abcd") + 2, MinecraftFont.width(component))
    }

    @Test
    fun `bold inherited from a parent applies to children`() {
        val component = Component.text("")
            .decoration(TextDecoration.BOLD, true)
            .append(Component.text("ab"))

        assertEquals(MinecraftFont.width("ab") + 2, MinecraftFont.width(component))
    }

    @Test
    fun `a child that turns bold off is not measured as bold`() {
        val component = Component.text("")
            .decoration(TextDecoration.BOLD, true)
            .append(Component.text("ab").decoration(TextDecoration.BOLD, false))

        assertEquals(MinecraftFont.width("ab"), MinecraftFont.width(component))
    }

    @Test
    fun `centre pads by whole spaces on the leading side only`() {
        val padding = MinecraftFont.centre("ab", availableWidth = 80)

        // "ab" is 12px, leaving 68px of slack; half is 34px, which is 8 whole spaces.
        assertEquals(8, padding.length)
        assertTrue(padding.all { it == ' ' })
    }

    @Test
    fun `text wider than the space available gets no padding rather than negative padding`() {
        assertEquals("", MinecraftFont.centre("a very long line indeed", availableWidth = 10))
    }

    @Test
    fun `a wider string needs less padding to sit centred`() {
        val narrow = MinecraftFont.centre("ii", availableWidth = 200).length
        val wide = MinecraftFont.centre("MM", availableWidth = 200).length

        assertTrue(narrow > wide, "narrower text must be pushed further right to centre")
    }
}

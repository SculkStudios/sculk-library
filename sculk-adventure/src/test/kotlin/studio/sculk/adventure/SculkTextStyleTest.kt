package studio.sculk.adventure

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextDecoration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SculkTextStyleTest {
    @AfterEach
    fun reset() {
        SculkTextStyle.clearShadow()
        SculkTextStyle.suppressItalics(false)
    }

    @Test
    fun `nothing is applied until defaults are set`() {
        assertNull(parseMessage("<green>hi").shadowColor())
    }

    @Test
    fun `a configured shadow reaches parsed text`() {
        SculkTextStyle.shadow(0x99000000.toInt())

        assertEquals(ShadowColor.shadowColor(0x99000000.toInt()), parseMessage("<green>hi").shadowColor())
    }

    @Test
    fun `a shadow already on the component wins over the default`() {
        SculkTextStyle.shadow(0x99000000.toInt())
        val explicit = Component.text("hi").shadowColor(ShadowColor.shadowColor(0xFFFF0000.toInt()))

        assertEquals(ShadowColor.shadowColor(0xFFFF0000.toInt()), SculkTextStyle.apply(explicit).shadowColor())
    }

    @Test
    fun `six digit hex is treated as fully opaque`() {
        assertTrue(SculkTextStyle.shadow("#112233"))

        assertEquals(ShadowColor.shadowColor(0xFF112233.toInt()), SculkTextStyle.shadow())
    }

    @Test
    fun `eight digit hex keeps its alpha`() {
        assertTrue(SculkTextStyle.shadow("#80112233"))

        assertEquals(ShadowColor.shadowColor(0x80112233.toInt()), SculkTextStyle.shadow())
    }

    @Test
    fun `an unparseable colour is refused instead of throwing`() {
        assertFalse(SculkTextStyle.shadow("not-a-colour"))
        assertNull(SculkTextStyle.shadow())
    }

    @Test
    fun `italics are suppressed only when asked, and never override an explicit choice`() {
        SculkTextStyle.suppressItalics(true)

        assertEquals(TextDecoration.State.FALSE, parseMessage("hi").decoration(TextDecoration.ITALIC))
        assertEquals(TextDecoration.State.TRUE, parseMessage("<i>hi").decoration(TextDecoration.ITALIC))
    }

    @Test
    fun `apply leaves a component untouched when nothing is configured`() {
        val plain = Component.text("hi")

        assertEquals(plain, SculkTextStyle.apply(plain))
    }
}

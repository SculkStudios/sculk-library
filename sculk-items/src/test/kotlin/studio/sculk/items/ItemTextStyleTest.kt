package studio.sculk.items

import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextDecoration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.sculk.adventure.SculkTextStyle

class ItemTextStyleTest {
    @AfterEach
    fun reset() = SculkTextStyle.clearShadow()

    @Test
    fun `item text picks up the server drop shadow`() {
        SculkTextStyle.shadow(0x99101014.toInt())

        assertEquals(ShadowColor.shadowColor(0x99101014.toInt()), parseItemText("<yellow>Harvester").shadowColor())
    }

    @Test
    fun `item text is upright regardless of the server italics setting`() {
        SculkTextStyle.suppressItalics(false)

        assertEquals(TextDecoration.State.FALSE, parseItemText("Harvester").decoration(TextDecoration.ITALIC))
    }
}

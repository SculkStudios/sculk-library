package studio.sculk.core.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GuiSlotsTest {
    @Test
    fun `horizontal border includes top and bottom rows`() {
        assertEquals(((0..8) + (18..26)).toList(), GuiSlots.horizontalBorder(27))
        assertEquals(((0..8) + (27..35)).toList(), GuiSlots.horizontalBorder(36))
        assertEquals(((0..8) + (45..53)).toList(), GuiSlots.horizontalBorder(54))
    }

    @Test
    fun `outer ring includes side columns`() {
        assertEquals(
            listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26),
            GuiSlots.outerRing(27),
        )
    }

    @Test
    fun `invalid gui sizes fail clearly`() {
        assertThrows(IllegalArgumentException::class.java) { GuiSlots.horizontalBorder(10) }
    }
}

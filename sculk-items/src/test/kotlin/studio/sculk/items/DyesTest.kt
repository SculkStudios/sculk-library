package studio.sculk.items

import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DyesTest {
    @Test
    fun `an exact dye colour resolves to that dye`() {
        assertEquals(Material.RED_DYE, Dyes.nearest(0xB02E26))
        assertEquals(Material.LIME_DYE, Dyes.nearest(0x80C71F))
    }

    @Test
    fun `an arbitrary colour resolves to the nearest dye`() {
        assertEquals(Material.RED_DYE, Dyes.nearest(0xFF0000))
        assertEquals(Material.BLACK_DYE, Dyes.nearest(0x000000))
        assertEquals(Material.WHITE_DYE, Dyes.nearest(0xFFFFFF))
    }

    @Test
    fun `a hex string is accepted with or without the hash`() {
        assertEquals(Material.RED_DYE, Dyes.nearest("#B02E26"))
        assertEquals(Material.RED_DYE, Dyes.nearest("B02E26"))
    }

    @Test
    fun `an unreadable hex string falls back to white rather than throwing`() {
        assertEquals(Material.WHITE_DYE, Dyes.nearest("not-a-colour"))
    }
}

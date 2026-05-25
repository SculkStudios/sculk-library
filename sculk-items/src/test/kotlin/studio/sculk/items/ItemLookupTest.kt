package studio.sculk.items

import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ItemLookupTest {
    @Test
    fun `normalizes simple and namespaced keys`() {
        assertEquals("diamond_sword", normalizeLookupKey(" DIAMOND_SWORD "))
        assertEquals("sharpness", normalizeLookupKey("minecraft:Sharpness"))
    }

    @Test
    fun `resolves common material key shapes`() {
        assertEquals(Material.DIAMOND_SWORD, materialByKey("diamond_sword"))
        assertEquals(Material.DIAMOND_SWORD, materialByKey("minecraft:diamond_sword"))
        assertEquals(Material.DIAMOND_SWORD, materialByKey("diamond sword"))
        assertNull(materialByKey("not_a_real_material"))
    }
}

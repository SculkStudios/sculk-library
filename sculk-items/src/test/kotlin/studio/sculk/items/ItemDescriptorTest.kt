package studio.sculk.items

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ItemDescriptorTest {
    @Test
    fun `descriptor keeps config friendly defaults`() {
        val descriptor = ItemDescriptor(material = "diamond_sword")

        assertEquals("diamond_sword", descriptor.material)
        assertEquals(1, descriptor.amount)
        assertTrue(descriptor.lore.isEmpty())
        assertTrue(descriptor.enchantments.isEmpty())
        assertTrue(descriptor.data.isEmpty())
    }

    @Test
    fun `descriptor supports rich item config data`() {
        val descriptor =
            ItemDescriptor(
                material = "diamond_sword",
                name = "<aqua>Starter Sword",
                lore = listOf("<gray>A clean starter weapon."),
                amount = 1,
                enchantments = mapOf("sharpness" to 5),
                glint = true,
                customModelData = 1001,
                unbreakable = true,
                data = mapOf("starter_item" to "true"),
            )

        assertEquals("sharpness", descriptor.enchantments.keys.single())
        assertEquals(1001, descriptor.customModelData)
        assertEquals("true", descriptor.data["starter_item"])
    }
}

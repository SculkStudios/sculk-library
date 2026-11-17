package studio.sculk.item

import net.kyori.adventure.key.Key
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemRarity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.pdc.PdcCodec
import studio.sculk.pdc.PdcKey

/** Specs are data: built, compared and rejected without a server. */
class ItemSpecTest {
    private val sharpness = Key.key("minecraft:sharpness")
    private val owner = PdcKey(NamespacedKey("shop", "owner"), PdcCodec.STRING)

    private val blade = item(Material.DIAMOND_SWORD) {
        name = "<primary>Sculk Blade"
        lore("<muted>Hums when the Warden is near.")
        enchant(sharpness, 5)
        hide(TooltipPart.ENCHANTMENTS)
        unbreakable = true
        id = Key.key("shop:sculk_blade")
        data(owner, "Daisy")
    }

    @Test
    fun `two specs built the same way are equal, and a changed copy is not`() {
        val again = item(Material.DIAMOND_SWORD) {
            name = "<primary>Sculk Blade"
            lore("<muted>Hums when the Warden is near.")
            enchant(sharpness, 5)
            hide(TooltipPart.ENCHANTMENTS)
            unbreakable = true
            id = Key.key("shop:sculk_blade")
            data(owner, "Daisy")
        }
        assertEquals(blade, again)
        assertEquals(blade.hashCode(), again.hashCode())
        val sharper = blade.with { enchant(sharpness, 6) }
        assertNotEquals(blade, sharper)
        assertEquals(mapOf(sharpness to 6), sharper.enchantments)
        assertEquals(blade.lore, sharper.lore)
    }

    @Test
    fun `a change to any one field makes a spec unequal, before and after its hash is cached`() {
        val variants = listOf<ItemSpecBuilder.() -> Unit>(
            { type = Material.NETHERITE_SWORD },
            { amount = 1 },
            { name = "<primary>Other" },
            { lore("<muted>More") },
            { enchant(Key.key("minecraft:smite"), 1) },
            { glint = false },
            { unbreakable = true },
            { hideTooltip = true },
            { hide(TooltipPart.ATTRIBUTES) },
            { model = Key.key("shop:blade") },
            { customModel = CustomModel(floats = listOf(1f)) },
            { maxStackSize = 64 },
            { maxDamage = 100 },
            { damage = 0 },
            { rarity = ItemRarity.EPIC },
            { dyedColor = Color.RED },
            { id = Key.key("shop:other") },
            { data(owner, "Alex") },
            { value("price", 5) },
        ).map { change -> item(Material.DIAMOND_SWORD) { amount = 2 }.with(change) }
        val base = item(Material.DIAMOND_SWORD) { amount = 2 }
        variants.forEach { assertNotEquals(base, it, "$it") }
        (variants + base).forEach { it.hashCode() }
        variants.forEach { assertNotEquals(base, it, "$it with cached hashes") }
        val again = item(Material.DIAMOND_SWORD) { amount = 2 }
        assertEquals(base, again)
        assertEquals(base.hashCode(), again.hashCode())
    }

    @Test
    fun `a spec that cannot exist fails where it is declared`() {
        assertThrows<IllegalArgumentException> { item(Material.STONE) { amount = 0 } }
        assertThrows<IllegalArgumentException> { item(Material.STONE) { amount = 100 } }
        assertThrows<IllegalArgumentException> { item(Material.STONE) { name = " " } }
        assertThrows<IllegalArgumentException> { item(Material.STONE) { enchant(sharpness, 256) } }
        assertThrows<IllegalArgumentException> {
            item(Material.STONE) {
                maxStackSize = 16
                amount = 32
            }
        }
        assertThrows<IllegalArgumentException> {
            item(Material.STONE) {
                maxDamage = 10
                damage = 11
            }
        }
        assertThrows<IllegalArgumentException> {
            item(Material.STONE) {
                maxDamage = 10
                maxStackSize = 16
            }
        }
    }

    @Test
    fun `persistent data set twice under one key keeps the last value`() {
        val spec = item(Material.STONE) {
            data(owner, "Alex")
            data(owner, "Steve")
        }
        assertEquals(1, spec.data.size)
        assertEquals("Steve", spec.data.single().value)
    }

    @Test
    fun `values are part of a spec, survive a copy and must be tag names`() {
        val head = item(Material.PLAYER_HEAD) {
            name = "<primary><player>"
            value("player", "Daisy")
        }
        assertEquals(mapOf("player" to "Daisy"), head.values)
        assertNotEquals(head, head.with { value("player", "Alex") })
        assertEquals(head, head.with { lore("<muted>Online") }.with { lore(emptyList()) })
        assertThrows<IllegalArgumentException> { item(Material.STONE) { value("Not A Tag", 1) } }
    }
}

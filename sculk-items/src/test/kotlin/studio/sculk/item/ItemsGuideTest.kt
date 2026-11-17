package studio.sculk.item

import net.kyori.adventure.key.Key
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.sculk.getOrNull
import studio.sculk.pdc.PdcCodec
import studio.sculk.pdc.PdcKey
import studio.sculk.pdc.read
import studio.sculk.pdc.write
import studio.sculk.testkit.paper.FakePersistentDataContainer
import java.util.UUID

/**
 * The examples in docs/src/content/docs/guides/items.mdx that need no server, compiled and run;
 * `integration-tests/items` runs the rest on every anchor.
 */
class ItemsGuideTest {
    private val owner = PdcKey(NamespacedKey("shop", "owner"), PdcCodec.UUID)
    private val tags = PdcKey(NamespacedKey("shop", "tags"), PdcCodec.STRING.list())

    @Test
    fun `the guide's blade is plain data and a changed copy keeps the rest`() {
        val blade = item(Material.DIAMOND_SWORD) {
            name = "<gradient:#7f5af0:#2cb67d>Sculk Blade"
            lore("<muted>Hums when the Warden is near.")
            enchant(Key.key("minecraft:sharpness"), 5)
            hide(TooltipPart.ENCHANTMENTS, TooltipPart.ATTRIBUTES)
            unbreakable = true
        }
        val marked = blade.with { id = Key.key("shop:sculk_blade") }
        assertEquals(Key.key("shop:sculk_blade"), marked.id)
        assertEquals(blade.lore, marked.lore)
        assertEquals(setOf(TooltipPart.ENCHANTMENTS, TooltipPart.ATTRIBUTES), marked.hidden)
    }

    @Test
    fun `the guide's persistent data entries read back what was written`() {
        val container = FakePersistentDataContainer()
        val id = UUID.randomUUID()
        container.write(owner, id)
        container.write(tags, listOf("rare", "shop"))
        assertEquals(id, container.read(owner).getOrNull())
        assertEquals(listOf("rare", "shop"), container.read(tags).getOrNull())
    }
}

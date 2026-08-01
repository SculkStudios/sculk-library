package studio.sculk.items

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import studio.sculk.SculkResult

class ItemBuilderTest {
    @BeforeEach
    fun setUp() {
        MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() = MockBukkit.unmock()

    private fun plain(component: Component?) = component?.let(PlainTextComponentSerializer.plainText()::serialize)

    @Test
    fun `material and amount carry through to the stack`() {
        val stack = item(Material.DIAMOND) { amount(16) }

        assertEquals(Material.DIAMOND, stack.type)
        assertEquals(16, stack.amount)
    }

    @Test
    fun `a name is rendered from MiniMessage rather than stored raw`() {
        val stack = item(Material.DIAMOND) { name("<red>Sharp Blade") }

        val name = stack.getData(DataComponentTypes.CUSTOM_NAME)
        assertEquals("Sharp Blade", plain(name), "the tags must be parsed, not left in the text")
    }

    @Test
    fun `a placeholder value is inserted literally`() {
        val stack = item(Material.DIAMOND) { name("<red><owner>'s blade", "owner" to "<bold>Daisy") }

        // The trust boundary: a player-supplied name is text, never markup. Without this a player
        // called `<bold>x` restyles any item naming them.
        assertEquals("<bold>Daisy's blade", plain(stack.getData(DataComponentTypes.CUSTOM_NAME)))
    }

    @Test
    fun `item text is not italic by default`() {
        val stack = item(Material.DIAMOND) { name("<red>Sharp Blade") }

        // Vanilla renders item names italic, which quietly undoes the styling above it.
        assertEquals(
            TextDecoration.State.FALSE,
            stack.getData(DataComponentTypes.CUSTOM_NAME)?.decoration(TextDecoration.ITALIC),
        )
    }

    @Test
    fun `lore keeps the order it was declared in`() {
        val stack = item(Material.DIAMOND) { lore("<gray>first", "<gray>second") }

        val lines = stack.getData(DataComponentTypes.LORE)!!.lines().map(::plain)
        assertEquals(listOf("first", "second"), lines)
    }

    @Test
    fun `lore accumulates across calls`() {
        val stack = item(Material.DIAMOND) {
            lore("<gray>first")
            lore(listOf("<gray>second"))
        }

        assertEquals(2, stack.getData(DataComponentTypes.LORE)!!.lines().size)
    }

    /** Enchantments are written as a data component, not through the legacy meta accessor. */
    private fun levelOf(stack: ItemStack, enchantment: Enchantment) =
        stack.getData(DataComponentTypes.ENCHANTMENTS)?.enchantments()?.get(enchantment) ?: 0

    @Test
    fun `an enchantment is applied at its level`() {
        val stack = item(Material.DIAMOND_SWORD) { enchant(Enchantment.SHARPNESS, 5) }

        assertEquals(5, levelOf(stack, Enchantment.SHARPNESS))
    }

    @Test
    fun `an enchantment resolved from a key matches the typed call`() {
        val byKey = item(Material.DIAMOND_SWORD) { enchant("sharpness", 3) }

        assertEquals(3, levelOf(byKey, Enchantment.SHARPNESS))
    }

    @Test
    fun `an unknown enchantment key costs the enchantment and not the item`() {
        // Enchantment keys arrive from config alongside material and model keys. Throwing here made
        // one typo in a kit definition fail the whole reward, where the other two keys do not.
        val stack = item(Material.DIAMOND_SWORD) { enchant("sharpnesss", 3) }

        assertEquals(Material.DIAMOND_SWORD, stack.type)
        assertEquals(0, levelOf(stack, Enchantment.SHARPNESS))
    }

    @Test
    fun `a good enchantment on the same item survives a bad one beside it`() {
        val stack = item(Material.DIAMOND_SWORD) {
            enchant("sharpnesss", 3)
            enchant("unbreaking", 2)
        }

        assertEquals(2, levelOf(stack, Enchantment.UNBREAKING))
    }

    @Test
    fun `persistent data round-trips by key`() {
        val stack = item(Material.DIAMOND) {
            pdc("sculk:kit", "starter")
            pdc("sculk:tier", 3)
        }

        val container = stack.itemMeta.persistentDataContainer
        assertEquals("starter", container.get(ItemKeys.of("sculk:kit"), PersistentDataType.STRING))
        assertEquals(3, container.get(ItemKeys.of("sculk:tier"), PersistentDataType.INTEGER))
    }

    @Test
    fun `a boolean is stored so it reads back as a boolean`() {
        val stack = item(Material.DIAMOND) { pdc("sculk:soulbound", true) }

        assertEquals(
            true,
            stack.itemMeta.persistentDataContainer.get(ItemKeys.of("sculk:soulbound"), PersistentDataType.BOOLEAN),
        )
    }

    @Test
    fun `a malformed model key is dropped rather than thrown`() {
        // Model ids come from config too, and a bad one should cost the model, not the item.
        val stack = item(Material.DIAMOND) { model("NOT A KEY") }

        assertEquals(Material.DIAMOND, stack.type)
    }

    @Test
    fun `unbreakable and glint are written as components`() {
        val stack = item(Material.DIAMOND_SWORD) {
            unbreakable()
            glint()
        }

        assertTrue(stack.hasData(DataComponentTypes.UNBREAKABLE))
        assertEquals(true, stack.getData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE))
    }

    @Test
    fun `each build returns an independent stack`() {
        val builder = ItemBuilder(Material.DIAMOND).apply { amount(4) }

        val first = builder.build()
        val second = builder.build()
        first.amount = 1

        // Menus build the same descriptor once per viewer; a shared stack would let one player's
        // change follow every other player's copy.
        assertFalse(first === second)
        assertEquals(4, second.amount)
    }

    @Test
    fun `a string material resolves through the same lookup as the typed call`() {
        val resolved = item("diamond_sword") { amount(2) }

        assertTrue(resolved.isSuccess)
        assertEquals(Material.DIAMOND_SWORD, resolved.getOrNull()!!.type)
    }

    @Test
    fun `an unknown material reports the key that failed`() {
        val resolved = item("diamon_sword")

        assertTrue(resolved.isFailure)
        val message = (resolved as SculkResult.Failure).message
        assertTrue(message.contains("diamon_sword"), "the message is what lands in the log: $message")
    }
}

package studio.sculk.items

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit

/**
 * Guards the version-compatibility layer that keeps items building across all of 1.21.x.
 *
 * The tests run against the newest paper-api, so every probe here should report the modern answer.
 * That is exactly what makes them worth having: [ItemCompat] finds the component API by *name*, and
 * a typo in one of those strings makes every probe quietly return false. Nothing else would notice —
 * items would still build, via the legacy path, on every server in the world.
 */
class ItemCompatTest {
    @BeforeEach
    fun setUp() {
        MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() = MockBukkit.unmock()

    @Test
    fun `every probe resolves against the compiled paper-api`() {
        assertTrue(ItemCompat.dataComponents, "ItemStack.setData was not found — the probe is looking for the wrong thing")
        assertTrue(ItemCompat.unbreakableIsMarker, "UNBREAKABLE should be the marker component on modern Paper")
        assertTrue(ItemCompat.tooltipDisplay, "TOOLTIP_DISPLAY should exist on modern Paper")
        assertTrue(ItemCompat.itemModel, "ITEM_MODEL should exist on modern Paper")
        assertTrue(ItemCompat.customModelDataComponent, "CustomModelData should exist on modern Paper")
    }

    @Test
    fun `a modern server needs no meta fallbacks`() {
        val spec = spec(hideVanillaTooltip = true, unbreakable = true, customModelData = 7)

        assertTrue(
            ModernItemWriter.fallbacks(spec).isEmpty(),
            "a server with every component should write components, not ItemMeta",
        )
    }

    @Test
    fun `the legacy writer reproduces name, lore and unbreakable through ItemMeta`() {
        val stack = ItemStack(Material.DIAMOND_SWORD)
        val meta = stack.itemMeta!!

        LegacyItemWriter
            .edits(
                spec(
                    displayName = Component.text("Daisy's blade"),
                    lore = listOf(Component.text("first"), Component.text("second")),
                    unbreakable = true,
                    hideVanillaTooltip = true,
                ),
            ).forEach { meta.it() }
        stack.itemMeta = meta

        assertEquals("Daisy's blade", plain(stack.itemMeta.displayName()))
        assertEquals(listOf("first", "second"), stack.itemMeta.lore()?.map(::plain))
        assertTrue(stack.itemMeta.isUnbreakable)
        assertTrue(stack.itemMeta.hasItemFlag(ItemFlag.HIDE_ENCHANTS), "hidden tooltips fall back to ItemFlag below 1.21.5")
    }

    @Test
    fun `a descriptor that does not ask for unbreakable never sets it`() {
        // The regression, and the reason this asserts on the spec rather than the finished stack:
        // toItemStack used to call unbreakable(false) unconditionally, so the UNBREAKABLE component
        // was touched for every item. On 1.21.4 that field has a different shape, the compiled
        // reference does not resolve, and every config-built item threw NoSuchFieldError. The
        // resulting *stack* looks identical either way — only the spec shows the difference.
        val untouched = ItemBuilder(Material.DIAMOND_SWORD).apply { applyDescriptor(ItemDescriptor(material = "diamond_sword")) }

        assertNull(untouched.spec().unbreakable, "an unset unbreakable must stay unset, not become an explicit false")
    }

    @Test
    fun `a descriptor that does ask for unbreakable still marks the item`() {
        val marked = ItemDescriptor(material = "diamond_sword", unbreakable = true).toItemStack().getOrNull()!!

        assertTrue(marked.hasData(DataComponentTypes.UNBREAKABLE))
    }

    private fun plain(component: Component?) =
        component?.let(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()::serialize)

    private fun spec(
        displayName: Component? = null,
        lore: List<Component> = emptyList(),
        customModelData: Int? = null,
        hideVanillaTooltip: Boolean = false,
        unbreakable: Boolean? = null,
    ) = ItemSpec(
        displayName = displayName,
        itemName = null,
        lore = lore,
        enchantments = emptyMap(),
        glint = null,
        customModelData = customModelData,
        model = null,
        hideVanillaTooltip = hideVanillaTooltip,
        unbreakable = unbreakable,
        damage = null,
        maxDamage = null,
        maxStackSize = null,
        rarity = null,
    )
}

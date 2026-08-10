package studio.sculk.items

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData
import io.papermc.paper.datacomponent.item.ItemEnchantments
import io.papermc.paper.datacomponent.item.ItemLore
import io.papermc.paper.datacomponent.item.TooltipDisplay
import net.kyori.adventure.text.Component
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemRarity
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.ItemMeta

/**
 * The resolved contents of an [ItemBuilder], handed to whichever writer this server can use.
 *
 * A plain carrier so the two writers share one shape and neither reaches back into the builder's
 * private state.
 */
internal class ItemSpec(
    val displayName: Component?,
    val itemName: Component?,
    val lore: List<Component>,
    val enchantments: Map<Enchantment, Int>,
    val glint: Boolean?,
    val customModelData: Int?,
    val model: NamespacedKey?,
    val hideVanillaTooltip: Boolean,
    val unbreakable: Boolean?,
    val damage: Int?,
    val maxDamage: Int?,
    val maxStackSize: Int?,
    val rarity: ItemRarity?,
)

/**
 * Writes an [ItemSpec] as data components — the path for any server that has them.
 *
 * Components that changed shape inside 1.21 are each isolated in their own method and guarded by an
 * [ItemCompat] probe, so an old server loses that one property instead of the whole item. See
 * [ItemCompat] for why guarding the reference is sufficient.
 */
internal object ModernItemWriter {
    /**
     * Meta edits this server needs because it cannot express the component form.
     *
     * Returned rather than applied so the caller can fold them into its single meta pass. Assigning
     * `stack.itemMeta` replaces the whole backing component map, so meta has to be written before
     * components, never after.
     */
    fun fallbacks(spec: ItemSpec): List<ItemMeta.() -> Unit> {
        val edits = mutableListOf<ItemMeta.() -> Unit>()
        // 1.21.4 models this as Valued<Unbreakable>; the 1.21.11-compiled NonValued reference would
        // not resolve against it. isUnbreakable means the same thing on every version.
        if (spec.unbreakable != null && !ItemCompat.unbreakableIsMarker) {
            edits += { isUnbreakable = spec.unbreakable }
        }
        // TOOLTIP_DISPLAY is 1.21.5+. ItemFlag is the mechanism it replaced and still works below it.
        if (spec.hideVanillaTooltip && !ItemCompat.tooltipDisplay) {
            edits += { addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE) }
        }
        if (spec.customModelData != null && !ItemCompat.customModelDataComponent) {
            edits += LegacyItemWriter.customModelData(spec.customModelData)
        }
        return edits
    }

    fun apply(stack: ItemStack, spec: ItemSpec) {
        spec.displayName?.let { stack.setData(DataComponentTypes.CUSTOM_NAME, it) }
        spec.itemName?.let { stack.setData(DataComponentTypes.ITEM_NAME, it) }
        if (spec.lore.isNotEmpty()) {
            val builder = ItemLore.lore()
            spec.lore.forEach { builder.addLine(it) }
            stack.setData(DataComponentTypes.LORE, builder.build())
        }
        if (spec.enchantments.isNotEmpty()) {
            val builder = ItemEnchantments.itemEnchantments()
            spec.enchantments.forEach { (enchantment, level) -> builder.add(enchantment, level) }
            stack.setData(DataComponentTypes.ENCHANTMENTS, builder.build())
        }
        spec.glint?.let { stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, it) }
        spec.customModelData?.let { applyCustomModelData(stack, it) }
        spec.model?.let { applyItemModel(stack, it) }
        if (spec.hideVanillaTooltip) applyHiddenTooltip(stack)
        spec.unbreakable?.let { applyUnbreakable(stack, it) }
        spec.maxStackSize?.let { stack.setData(DataComponentTypes.MAX_STACK_SIZE, it) }
        spec.maxDamage?.let { stack.setData(DataComponentTypes.MAX_DAMAGE, it) }
        spec.damage?.let { stack.setData(DataComponentTypes.DAMAGE, it) }
        spec.rarity?.let { stack.setData(DataComponentTypes.RARITY, it) }
    }

    /** Handled by [fallbacks] below 1.21.5, where the field is `Valued` and would not resolve. */
    private fun applyUnbreakable(stack: ItemStack, value: Boolean) {
        if (!ItemCompat.unbreakableIsMarker) return
        if (value) stack.setData(DataComponentTypes.UNBREAKABLE) else stack.unsetData(DataComponentTypes.UNBREAKABLE)
    }

    /** Handled by [fallbacks] below 1.21.5, where neither the component nor its class exists. */
    private fun applyHiddenTooltip(stack: ItemStack) {
        if (!ItemCompat.tooltipDisplay) return
        stack.setData(
            DataComponentTypes.TOOLTIP_DISPLAY,
            TooltipDisplay
                .tooltipDisplay()
                .addHiddenComponents(
                    DataComponentTypes.ENCHANTMENTS,
                    DataComponentTypes.ATTRIBUTE_MODIFIERS,
                    DataComponentTypes.UNBREAKABLE,
                ).build(),
        )
    }

    /** Handled by [fallbacks] below 1.21.4, where custom model data is still a bare integer. */
    private fun applyCustomModelData(stack: ItemStack, value: Int) {
        if (!ItemCompat.customModelDataComponent) return
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addFloat(value.toFloat()).build())
    }

    /** Resource-pack item models are 1.21.2+; below that the key is dropped with one log line. */
    private fun applyItemModel(stack: ItemStack, key: NamespacedKey) {
        if (!ItemCompat.itemModel) {
            ItemCompat.unsupported("item-model")
            return
        }
        stack.setData(DataComponentTypes.ITEM_MODEL, key)
    }
}

/**
 * Writes an [ItemSpec] through [ItemMeta] — the path for 1.21.0–1.21.3, which have no component API.
 *
 * Every call here predates data components and is still present (if deprecated) on current Paper, so
 * this compiles against the newest API and runs against the oldest. The deprecation suppression is
 * the point of the file, not an oversight: these are deliberately the old calls.
 */
@Suppress("DEPRECATION")
internal object LegacyItemWriter {
    fun edits(spec: ItemSpec): List<ItemMeta.() -> Unit> {
        val edits = mutableListOf<ItemMeta.() -> Unit>()
        spec.displayName?.let { name -> edits += { displayName(name) } }
        spec.itemName?.let { name -> edits += { itemName(name) } }
        if (spec.lore.isNotEmpty()) edits += { lore(spec.lore) }
        if (spec.enchantments.isNotEmpty()) {
            edits += { spec.enchantments.forEach { (enchantment, level) -> addEnchant(enchantment, level, true) } }
        }
        spec.glint?.let { value -> edits += { setEnchantmentGlintOverride(value) } }
        spec.customModelData?.let { edits += customModelData(it) }
        if (spec.hideVanillaTooltip) {
            edits += { addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE) }
        }
        spec.unbreakable?.let { value -> edits += { isUnbreakable = value } }
        spec.maxStackSize?.let { value -> edits += { setMaxStackSize(value) } }
        spec.rarity?.let { value -> edits += { setRarity(value) } }
        spec.maxDamage?.let { value -> edits += { (this as? Damageable)?.setMaxDamage(value) } }
        spec.damage?.let { value -> edits += { (this as? Damageable)?.damage = value } }
        // No ItemMeta equivalent below 1.21.2 — the model key is dropped, once, with a log line.
        if (spec.model != null) ItemCompat.unsupported("item-model")
        return edits
    }

    fun customModelData(value: Int): ItemMeta.() -> Unit = { setCustomModelData(value) }
}

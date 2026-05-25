package studio.sculk.items

import net.kyori.adventure.text.Component
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack

/**
 * Config-friendly item representation for common plugin items.
 */
public data class ItemDescriptor(
    public val material: String,
    public val name: String? = null,
    public val lore: List<String> = emptyList(),
    public val amount: Int = 1,
    public val enchantments: Map<String, Int> = emptyMap(),
    public val flags: List<String> = emptyList(),
    public val glint: Boolean = false,
    public val customModelData: Int? = null,
    public val unbreakable: Boolean = false,
    public val data: Map<String, String> = emptyMap(),
)

/** Converts this descriptor to an [ItemStack], or null if its material is unknown. */
public fun ItemDescriptor.toItemStack(): ItemStack? =
    item(material) {
        amount(amount)
        name?.let { name(it) }
        if (lore.isNotEmpty()) lore(lore)
        enchantments.forEach { (key, level) ->
            enchantmentByKey(key)?.let { enchant(it, level) }
        }
        flags
            .mapNotNull { flag -> ItemFlag.entries.firstOrNull { it.name.equals(flag, ignoreCase = true) } }
            .takeIf { it.isNotEmpty() }
            ?.let { flag(*it.toTypedArray()) }
        if (glint) glint()
        customModelData?.let { customModelData(it) }
        unbreakable(unbreakable)
        data.forEach { (key, value) -> pdc(key, value) }
    }

/** Converts an [ItemStack] into a compact [ItemDescriptor]. */
public fun ItemStack.toDescriptor(): ItemDescriptor {
    val meta = itemMeta
    val name = meta?.displayName()?.let(::serializeComponentOrNull)
    val lore = meta?.lore()?.mapNotNull(::serializeComponentOrNull).orEmpty()
    val enchantments =
        enchantments
            .mapKeys { it.key.key.key }
            .mapValues { it.value }
    val flags = meta?.itemFlags?.map { it.name.lowercase() }.orEmpty()
    val customModelData =
        meta
            ?.customModelDataComponent
            ?.floats
            ?.firstOrNull()
            ?.toInt()
            ?.takeIf { it > 0 }

    return ItemDescriptor(
        material = type.key.key,
        name = name,
        lore = lore,
        amount = amount,
        enchantments = enchantments,
        flags = flags,
        glint = meta?.enchantmentGlintOverride == true,
        customModelData = customModelData,
        unbreakable = meta?.isUnbreakable == true,
    )
}

private fun serializeComponentOrNull(component: Component): String? = serializeItemText(component).takeIf { it.isNotBlank() }

package studio.sculk.items

import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/** Builds an [ItemStack] from a [Material]. */
public fun item(
    material: Material,
    block: ItemBuilder.() -> Unit = {},
): ItemStack = ItemBuilder(material).apply(block).build()

/**
 * Builds an [ItemStack] from a modern material key.
 *
 * Returns null if [material] is unknown.
 */
public fun item(
    material: String,
    block: ItemBuilder.() -> Unit = {},
): ItemStack? = materialByKey(material)?.let { ItemBuilder(it).apply(block).build() }

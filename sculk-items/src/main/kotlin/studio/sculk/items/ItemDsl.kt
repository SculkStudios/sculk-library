package studio.sculk.items

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.series.SculkSeries
import studio.sculk.text.SculkMessages

/** Builds an [ItemStack] from a [Material]. */
@SculkStable
public fun item(material: Material, block: ItemBuilder.() -> Unit = {}): ItemStack = ItemBuilder(material).apply(block).build()

/**
 * Builds an [ItemStack] from a material key, reporting an unknown key rather than returning null.
 *
 * The key almost always comes from a config file, so the caller wants the name that was wrong in
 * their log, not a null to trace back. This used to return null while `ItemBuilder.material(String)`
 * threw for the identical failure, in the same module.
 */
@SculkStable
public fun item(material: String, block: ItemBuilder.() -> Unit = {}): SculkResult<ItemStack> = SculkSeries.material(material)
    ?.let { SculkResult.success(ItemBuilder(it).apply(block).build()) }
    ?: SculkResult.failure("No material named '$material'.")

/** Builds an [ItemStack] whose name and lore render with this renderer's theme. */
@SculkStable
public fun SculkMessages.item(material: Material, block: ItemBuilder.() -> Unit = {}): ItemStack =
    ItemBuilder(material, this).apply(block).build()

/** Builds an [ItemStack] from a material key, with theme-rendered text. */
@SculkStable
public fun SculkMessages.item(material: String, block: ItemBuilder.() -> Unit = {}): SculkResult<ItemStack> = SculkSeries.material(material)
    ?.let { SculkResult.success(ItemBuilder(it, this).apply(block).build()) }
    ?: SculkResult.failure("No material named '$material'.")

@file:JvmName("SculkItems")
@file:JvmMultifileClass

package studio.sculk.items

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import studio.sculk.annotation.SculkStable
import java.util.function.Consumer

/** Builds an [ItemStack] from a [Material]. */
@JvmOverloads
@SculkStable
public fun item(material: Material, block: ItemBuilder.() -> Unit = {}): ItemStack = ItemBuilder(material).apply(block).build()

/**
 * Builds an [ItemStack] from a modern material key.
 *
 * Returns null if [material] is unknown.
 */
@JvmOverloads
@SculkStable
public fun item(material: String, block: ItemBuilder.() -> Unit = {}): ItemStack? =
    materialByKey(material)?.let { ItemBuilder(it).apply(block).build() }

/**
 * Java-friendly overload of [item] taking a [Consumer].
 *
 * ```java
 * ItemStack sword = SculkItems.item(Material.DIAMOND_SWORD, b -> {
 *     b.name("<red>Excalibur");
 *     b.amount(1);
 * });
 * ```
 */
@SculkStable
public fun item(material: Material, block: Consumer<ItemBuilder>): ItemStack = ItemBuilder(material).also { block.accept(it) }.build()

/** Java-friendly overload of [item] from a material key, taking a [Consumer]. Returns null if unknown. */
@SculkStable
public fun item(material: String, block: Consumer<ItemBuilder>): ItemStack? =
    materialByKey(material)?.let { mat -> ItemBuilder(mat).also { block.accept(it) }.build() }

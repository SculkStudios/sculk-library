package gg.sculk.core.gui

import gg.sculk.core.adventure.parseMessage
import gg.sculk.core.annotation.SculkInternal
import gg.sculk.core.annotation.SculkStable
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * An immutable definition of a single slot in a [Gui].
 *
 * Defined via the `item(slot) { ... }` DSL inside a [GuiBuilder].
 */
@SculkStable
public class GuiItem
    @SculkInternal
    constructor(
        public val slot: Int,
        public val stack: ItemStack,
        internal val clickHandler: (GuiContext.() -> Unit)?,
    )

/**
 * DSL builder for a [GuiItem].
 */
@SculkStable
public class GuiItemBuilder
    @SculkInternal
    constructor(
        private val slot: Int,
    ) {
        /** The material of this item. Defaults to [Material.AIR]. */
        public var material: Material = Material.AIR

        /** The display name, parsed as MiniMessage. */
        public var name: String = ""

        /** Lore lines, each parsed as MiniMessage. */
        public val lore: MutableList<String> = mutableListOf()

        /** The stack size. Defaults to 1. */
        public var amount: Int = 1

        private var clickHandler: (GuiContext.() -> Unit)? = null

        /** Registers a click handler for this item. */
        public fun onClick(block: GuiContext.() -> Unit) {
            clickHandler = block
        }

        /** Adds a lore line. */
        public fun lore(vararg lines: String) {
            lore.addAll(lines)
        }

        @SculkInternal
        public fun build(): GuiItem {
            val stack = ItemStack(material, amount)
            val meta = stack.itemMeta
            if (meta != null) {
                if (name.isNotBlank()) meta.displayName(parseMessage(name))
                if (lore.isNotEmpty()) meta.lore(lore.map { parseMessage(it) })
                stack.itemMeta = meta
            }
            return GuiItem(slot, stack, clickHandler)
        }
    }

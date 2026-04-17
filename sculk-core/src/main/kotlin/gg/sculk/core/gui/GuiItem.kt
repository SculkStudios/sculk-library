package gg.sculk.core.gui

import gg.sculk.core.adventure.parseMessage
import gg.sculk.core.annotation.SculkInternal
import gg.sculk.core.annotation.SculkStable
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack

/**
 * An immutable definition of a single slot in a [Gui].
 *
 * Defined via the `item(slot) { ... }` DSL inside a [GuiBuilder].
 *
 * If a [dynamicBuilder] is set, the displayed [ItemStack] is computed per-player
 * when the GUI opens rather than being fixed at definition time.
 */
@SculkStable
public class GuiItem
    @SculkInternal
    constructor(
        public val slot: Int,
        public val stack: ItemStack,
        @SculkInternal public val clickHandler: (GuiContext.() -> Unit)?,
        /** Optional per-player builder that overrides [stack] when the GUI opens. */
        @SculkInternal public val dynamicBuilder: (GuiItemBuilder.(Player) -> Unit)?,
    ) {
        /**
         * Returns the [ItemStack] to display for [player].
         *
         * If [dynamicBuilder] is set, it is evaluated using a fresh [GuiItemBuilder]
         * seeded with this item's static values, then built. Otherwise returns [stack].
         */
        @SculkInternal
        public fun resolveStack(player: Player?): ItemStack {
            if (dynamicBuilder == null || player == null) return stack
            @OptIn(SculkInternal::class)
            val itemBuilder = GuiItemBuilder(slot)
            itemBuilder.material = stack.type
            itemBuilder.amount = stack.amount
            dynamicBuilder.invoke(itemBuilder, player)
            return itemBuilder.build().stack
        }
    }

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

        /**
         * When `true`, applies an invisible enchantment to give this item the enchantment glow effect.
         *
         * The enchantment name is hidden — only the shimmer is visible.
         */
        public var glow: Boolean = false

        /**
         * Custom model data value for resource-pack item overrides.
         *
         * Set to any positive integer to apply a `CustomModelData` tag:
         * ```kotlin
         * item(4) {
         *     material = Material.STICK
         *     customModelData = 1001
         *     name = "<gold>Magic Wand"
         * }
         * ```
         */
        public var customModelData: Int = 0

        private val enchantments: MutableMap<String, Int> = mutableMapOf()
        private var clickHandler: (GuiContext.() -> Unit)? = null
        private var dynamicBuilder: (GuiItemBuilder.(Player) -> Unit)? = null

        /** Registers a click handler for this item. */
        public fun onClick(block: GuiContext.() -> Unit) {
            clickHandler = block
        }

        /**
         * Registers a per-player content builder that is evaluated when the GUI opens.
         *
         * Use this for items that should differ between players — showing player-specific
         * data, toggling materials based on permissions, etc.
         *
         * ```kotlin
         * item(4) {
         *     material = Material.STONE  // static fallback
         *     dynamicContent { player ->
         *         material = if (player.hasPermission("vip")) Material.DIAMOND else Material.STONE
         *         name = "<aqua>Welcome, ${player.name}"
         *     }
         *     onClick { reply("<green>Clicked!") }
         * }
         * ```
         */
        public fun dynamicContent(block: GuiItemBuilder.(Player) -> Unit) {
            dynamicBuilder = block
        }

        /** Adds lore lines. */
        public fun lore(vararg lines: String) {
            lore.addAll(lines)
        }

        /**
         * Adds an enchantment by its Minecraft key (e.g. `"sharpness"`, `"unbreaking"`).
         *
         * Unsafe levels are allowed — useful for display items. The enchantment is
         * looked up via the Bukkit [Registry] so it works across MC versions.
         *
         * ```kotlin
         * item(0) {
         *     material = Material.DIAMOND_SWORD
         *     enchantment("sharpness", 5)
         *     enchantment("unbreaking", 3)
         * }
         * ```
         */
        public fun enchantment(
            name: String,
            level: Int,
        ) {
            enchantments[name.lowercase()] = level
        }

        @SculkInternal
        public fun build(): GuiItem {
            val stack = ItemStack(material, amount)
            val meta = stack.itemMeta
            if (meta != null) {
                if (name.isNotBlank()) meta.displayName(parseMessage(name))
                if (lore.isNotEmpty()) meta.lore(lore.map { parseMessage(it) })
                if (glow) {
                    val enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"))
                    if (enchantment != null) meta.addEnchant(enchantment, 1, true)
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
                }
                if (customModelData != 0) {
                    meta.setCustomModelData(customModelData)
                }
                for ((enchName, level) in enchantments) {
                    val ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(enchName))
                    if (ench != null) meta.addEnchant(ench, level, true)
                }
                stack.itemMeta = meta
            }
            return GuiItem(slot, stack, clickHandler, dynamicBuilder)
        }
    }

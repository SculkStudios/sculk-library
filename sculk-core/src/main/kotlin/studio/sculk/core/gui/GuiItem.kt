package studio.sculk.core.gui

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import studio.sculk.core.annotation.SculkInternal
import studio.sculk.core.annotation.SculkStable
import studio.sculk.items.ItemBuilder

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
         * The DSL remains an [Int] for simple resource-pack model overrides.
         * Internally, Sculk writes Paper's modern custom model data component,
         * where the integer is represented as a single float value.
         *
         * Set to any positive integer to apply custom model data:
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
        private var stackBuilder: (ItemBuilder.() -> Unit)? = null
        private var explicitStack: ItemStack? = null

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
         * Builds this GUI item stack with the shared `sculk-items` builder.
         *
         * This is the preferred API for new code because it exposes the same
         * metadata, PDC, model-data, and enchantment behavior as standalone items.
         */
        public fun stack(block: ItemBuilder.() -> Unit) {
            stackBuilder = block
        }

        /**
         * Uses a complete [ItemStack] for this GUI item.
         *
         * This is useful when another Sculk item builder already produced the
         * final stack, such as player skulls, config-backed descriptors, or
         * custom metadata that should not be rebuilt through GUI defaults.
         */
        public fun stack(stack: ItemStack) {
            explicitStack = stack.clone()
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
            val stack =
                explicitStack?.clone()
                    ?: studio.sculk.items.item(material) {
                        amount(amount)
                        if (name.isNotBlank()) name(name)
                        if (lore.isNotEmpty()) lore(lore)
                        if (glow) glint()
                        if (customModelData != 0) customModelData(customModelData)
                        for ((enchName, level) in enchantments) enchant(enchName, level)
                        stackBuilder?.invoke(this)
                    }
            return GuiItem(slot, stack, clickHandler, dynamicBuilder)
        }
    }

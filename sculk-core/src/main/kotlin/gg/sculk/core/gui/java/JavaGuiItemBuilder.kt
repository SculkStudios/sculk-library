package gg.sculk.core.gui.java

import gg.sculk.core.annotation.SculkStable
import gg.sculk.core.gui.GuiContext
import gg.sculk.core.gui.GuiItemBuilder
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.function.BiConsumer
import java.util.function.Consumer

/**
 * Fluent Java-compatible item builder for Sculk Studio GUIs.
 *
 * Kotlin users should use the `item(slot) { }` DSL inside [gg.sculk.core.gui.gui] instead.
 *
 * Obtained from [JavaGuiBuilder.item] or [JavaGuiItemBuilder.dynamicContent] — never constructed directly.
 */
@SculkStable
public class JavaGuiItemBuilder internal constructor(
    private val builder: GuiItemBuilder,
) {
    /** Sets the item material. */
    public fun material(material: Material): JavaGuiItemBuilder {
        builder.material = material
        return this
    }

    /** Sets the display name, parsed as MiniMessage. */
    public fun name(name: String): JavaGuiItemBuilder {
        builder.name = name
        return this
    }

    /** Adds lore lines, each parsed as MiniMessage. */
    public fun lore(vararg lines: String): JavaGuiItemBuilder {
        builder.lore(*lines)
        return this
    }

    /** Sets the stack size. Defaults to 1. */
    public fun amount(amount: Int): JavaGuiItemBuilder {
        builder.amount = amount
        return this
    }

    /** Adds an enchantment shimmer without showing enchantment text. */
    public fun glow(glow: Boolean): JavaGuiItemBuilder {
        builder.glow = glow
        return this
    }

    /** Registers a click handler for this item. */
    public fun onClick(handler: Consumer<GuiContext>): JavaGuiItemBuilder {
        builder.onClick { handler.accept(this) }
        return this
    }

    /**
     * Registers a per-player content builder that runs when the GUI opens.
     *
     * Use this for items whose display differs between players — showing balances,
     * toggling materials based on permissions, etc.
     *
     * ```java
     * .item(4, item -> item
     *     .material(Material.PLAYER_HEAD)
     *     .dynamicContent((builder, player) -> {
     *         builder.name("<aqua>Welcome, <white>" + player.getName());
     *         builder.lore("<gray>Coins: <gold>" + economy.getBalance(player));
     *     })
     * )
     * ```
     */
    public fun dynamicContent(handler: BiConsumer<JavaGuiItemBuilder, Player>): JavaGuiItemBuilder {
        builder.dynamicContent { player ->
            handler.accept(JavaGuiItemBuilder(this), player)
        }
        return this
    }
}

package studio.sculk.items.java

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import studio.sculk.items.ItemBuilder
import java.util.function.Consumer

/** Java-friendly fluent builder for Sculk item stacks. */
public class JavaItemBuilder private constructor(
    private val builder: ItemBuilder,
) {
    public fun name(value: String): JavaItemBuilder = apply { builder.name(value) }

    public fun lore(vararg lines: String): JavaItemBuilder = apply { builder.lore(*lines) }

    public fun amount(value: Int): JavaItemBuilder = apply { builder.amount(value) }

    public fun enchant(
        enchantment: Enchantment,
        level: Int,
    ): JavaItemBuilder = apply { builder.enchant(enchantment, level) }

    public fun enchant(
        key: String,
        level: Int,
    ): JavaItemBuilder = apply { builder.enchant(key, level) }

    public fun flag(vararg flags: ItemFlag): JavaItemBuilder = apply { builder.flag(*flags) }

    public fun glint(): JavaItemBuilder = apply { builder.glint() }

    public fun glint(value: Boolean): JavaItemBuilder = apply { builder.glint(value) }

    public fun unbreakable(): JavaItemBuilder = apply { builder.unbreakable() }

    public fun customModelData(value: Int): JavaItemBuilder = apply { builder.customModelData(value) }

    public fun pdc(
        key: String,
        value: String,
    ): JavaItemBuilder = apply { builder.pdc(key, value) }

    public fun pdc(
        key: String,
        value: Boolean,
    ): JavaItemBuilder = apply { builder.pdc(key, value) }

    public fun pdc(
        key: NamespacedKey,
        value: String,
    ): JavaItemBuilder = apply { builder.pdc(key, value) }

    public fun edit(handler: Consumer<ItemBuilder>): JavaItemBuilder = apply { handler.accept(builder) }

    public fun build(): ItemStack = builder.build()

    public companion object {
        @JvmStatic
        public fun of(material: Material): JavaItemBuilder = JavaItemBuilder(ItemBuilder(material))
    }
}

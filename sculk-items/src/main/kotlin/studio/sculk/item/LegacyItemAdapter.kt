package studio.sculk.item

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.DyedItemColor
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers
import io.papermc.paper.datacomponent.item.ItemEnchantments
import io.papermc.paper.datacomponent.item.Unbreakable
import org.bukkit.inventory.ItemStack

/**
 * Paper 1.21.4, before the 1.21.5 tooltip rework: each component carries its own `showInTooltip`
 * flag and the whole tooltip hides through `HIDE_TOOLTIP`. Only [ItemCompiler] instantiates this,
 * and only when `TooltipDisplay` is absent, because these members no longer exist above 1.21.4.
 */
internal class LegacyItemAdapter : ItemAdapter {
    override fun unbreakable(stack: ItemStack) {
        stack.setData(DataComponentTypes.UNBREAKABLE, Unbreakable.unbreakable(true))
    }

    override fun hide(stack: ItemStack, tooltip: Boolean, parts: Set<TooltipPart>) {
        if (tooltip) stack.setData(DataComponentTypes.HIDE_TOOLTIP)
        parts.forEach { part ->
            when (part) {
                TooltipPart.ENCHANTMENTS -> stack.getData(DataComponentTypes.ENCHANTMENTS)?.let { current ->
                    stack.setData(
                        DataComponentTypes.ENCHANTMENTS,
                        ItemEnchantments.itemEnchantments().addAll(current.enchantments()).showInTooltip(false).build(),
                    )
                }

                // The stack reports its type's default modifiers too, so hiding them keeps them in effect.
                TooltipPart.ATTRIBUTES -> stack.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS)?.let { current ->
                    val hidden = ItemAttributeModifiers.itemAttributes().showInTooltip(false)
                    current.modifiers().forEach { entry -> hidden.addModifier(entry.attribute(), entry.modifier()) }
                    stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, hidden.build())
                }

                TooltipPart.UNBREAKABLE -> if (stack.hasData(DataComponentTypes.UNBREAKABLE)) {
                    stack.setData(DataComponentTypes.UNBREAKABLE, Unbreakable.unbreakable(false))
                }

                TooltipPart.DYED_COLOR -> stack.getData(DataComponentTypes.DYED_COLOR)?.let { current ->
                    stack.setData(
                        DataComponentTypes.DYED_COLOR,
                        DyedItemColor.dyedItemColor().color(current.color()).showInTooltip(false).build(),
                    )
                }
            }
        }
    }
}

package studio.sculk.item.adapter

import io.papermc.paper.datacomponent.DataComponentType
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.TooltipDisplay
import org.bukkit.inventory.ItemStack
import studio.sculk.item.ItemAdapter
import studio.sculk.item.TooltipPart

/**
 * Paper 1.21.5 and later: one `TooltipDisplay` component hides the tooltip or named components, and
 * `UNBREAKABLE` carries no value. Instantiated by name only, after a probe found `TooltipDisplay`.
 */
internal class ModernItemAdapter : ItemAdapter {
    override fun unbreakable(stack: ItemStack) {
        stack.setData(DataComponentTypes.UNBREAKABLE)
    }

    override fun hide(stack: ItemStack, tooltip: Boolean, parts: Set<TooltipPart>) {
        val hidden: Set<DataComponentType> = parts.mapTo(LinkedHashSet()) { part ->
            when (part) {
                TooltipPart.ENCHANTMENTS -> DataComponentTypes.ENCHANTMENTS
                TooltipPart.ATTRIBUTES -> DataComponentTypes.ATTRIBUTE_MODIFIERS
                TooltipPart.UNBREAKABLE -> DataComponentTypes.UNBREAKABLE
                TooltipPart.DYED_COLOR -> DataComponentTypes.DYED_COLOR
            }
        }
        stack.setData(
            DataComponentTypes.TOOLTIP_DISPLAY,
            TooltipDisplay.tooltipDisplay().hideTooltip(tooltip).hiddenComponents(hidden).build(),
        )
    }
}
